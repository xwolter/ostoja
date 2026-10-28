/*
 * Ostoja - protokół rozszerzenia (ten sam format co aplikacja na Windows, desktop/Extension.kt).
 *
 * Parowanie: ECDH P-256 → wspólny sekret → HKDF-SHA256 → klucz kanału AES-256-GCM i 6-cyfrowy kod
 * do porównania w oknie aplikacji. Każde zapytanie: koperta {ts, nonce, body} szyfrowana z AAD
 * „ostoja1|req|<ścieżka>|<id rozszerzenia>”, odpowiedź z AAD „ostoja1|resp|…” i tym samym nonce.
 *
 * Plik działa w rozszerzeniu (tło, okienko) i w Node (testy protokołu z aplikacją).
 */
(function (root) {
  'use strict';
  const subtle = (root.crypto || globalThis.crypto).subtle;
  const cryptoObj = root.crypto || globalThis.crypto;
  const enc = new TextEncoder();
  const dec = new TextDecoder();
  // OSTOJA_BASE ustawiają tylko testy w Node (inny port, żeby nie ruszać działającej aplikacji).
  const BASE = root.OSTOJA_BASE || 'http://127.0.0.1:47811';

  const b64 = (bytes) => {
    let s = '';
    const a = new Uint8Array(bytes);
    for (let i = 0; i < a.length; i++) s += String.fromCharCode(a[i]);
    return btoa(s);
  };
  const unb64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
  const random = (n) => cryptoObj.getRandomValues(new Uint8Array(n));
  const token = () => b64(random(18)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const concat = (a, b) => { const o = new Uint8Array(a.length + b.length); o.set(a); o.set(b, a.length); return o; };

  async function hmac(keyBytes, data) {
    const k = await subtle.importKey('raw', keyBytes, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
    return new Uint8Array(await subtle.sign('HMAC', k, data));
  }

  /** HKDF-SHA256 dla jednego bloku - identycznie jak SyncCrypto.hkdf w Kotlinie. */
  async function hkdf(ikm, salt, info) {
    const prk = await hmac(enc.encode(salt), ikm);
    return hmac(prk, concat(enc.encode(info), new Uint8Array([1])));
  }

  async function aesKey(raw) {
    return subtle.importKey('raw', raw, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt']);
  }

  async function seal(rawKey, plain, aad) {
    const iv = random(12);
    const ct = new Uint8Array(await subtle.encrypt({ name: 'AES-GCM', iv, additionalData: enc.encode(aad), tagLength: 128 }, await aesKey(rawKey), plain));
    return concat(iv, ct);
  }

  async function open(rawKey, blob, aad) {
    const iv = blob.slice(0, 12);
    const pt = await subtle.decrypt({ name: 'AES-GCM', iv, additionalData: enc.encode(aad), tagLength: 128 }, await aesKey(rawKey), blob.slice(12));
    return new Uint8Array(pt);
  }

  const aad = (dir, path, device) => `ostoja1|${dir}|${path}|${device}`;

  class AppError extends Error {
    constructor(code, msg) { super(msg || code); this.code = code; }
  }

  async function http(path, opts = {}) {
    let res;
    try {
      res = await fetch(BASE + path, opts);
    } catch (e) {
      throw new AppError('offline', 'Aplikacja Ostoja nie działa na tym komputerze');
    }
    if (res.status === 410) throw new AppError('unpaired', 'Aplikacja nie zna już tego rozszerzenia');
    if (res.status === 403) throw new AppError('forbidden', 'Odrzucono zapytanie');
    if (res.status === 404) throw new AppError('outdated', 'Zaktualizuj aplikację Ostoja na Windows - ta wersja tego nie obsługuje');
    if (!res.ok) throw new AppError('http', 'HTTP ' + res.status);
    return res;
  }

  /** Klient kanału z aplikacją. `session` = {id, key (base64)}. */
  const Ostoja = {
    BASE,
    AppError,
    b64,
    unb64,
    token,
    hkdf,

    async hello() {
      const r = await http('/ext/hello');
      return r.json();
    },

    /** Krok 1 parowania: wymiana kluczy. Zwraca {id, key, code, computerName}. */
    async pair(name) {
      const kp = await subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']);
      const pub = new Uint8Array(await subtle.exportKey('spki', kp.publicKey));
      const id = token();
      const r = await http('/ext/pair', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id, name, pub: b64(pub) }) });
      const resp = await r.json();
      const peer = await subtle.importKey('spki', unb64(resp.pub), { name: 'ECDH', namedCurve: 'P-256' }, false, []);
      const shared = new Uint8Array(await subtle.deriveBits({ name: 'ECDH', public: peer }, kp.privateKey, 256));
      const key = await hkdf(shared, 'ostoja-ext-v1', 'channel');
      const h = await hkdf(shared, 'ostoja-ext-v1', 'code');
      const n = ((h[0] << 24) >>> 0) + (h[1] << 16) + (h[2] << 8) + h[3];
      const code = String(n % 1000000).padStart(6, '0');
      return { id, key: b64(key), code, computerName: resp.computerName };
    },

    /** Krok 2 parowania: czy użytkownik kliknął „Połącz” w aplikacji. waiting | ok | rejected | none */
    async pairStatus(id) {
      const r = await http('/ext/pair-status', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id }) });
      return (await r.json()).state;
    },

    /** Szyfrowane zapytanie do aplikacji. */
    async call(session, path, body = {}) {
      const key = unb64(session.key);
      const nonce = token();
      const env = JSON.stringify({ ts: Date.now(), nonce, body });
      const blob = await seal(key, enc.encode(env), aad('req', path, session.id));
      const r = await http('/ext/' + path, { method: 'POST', headers: { 'Content-Type': 'application/octet-stream', 'X-Ostoja-Device': session.id }, body: blob });
      const raw = new Uint8Array(await r.arrayBuffer());
      let out;
      try {
        out = JSON.parse(dec.decode(await open(key, raw, aad('resp', path, session.id))));
      } catch (e) {
        throw new AppError('crypto', 'Nie udało się odczytać odpowiedzi aplikacji');
      }
      if (out.nonce !== nonce) throw new AppError('crypto', 'Odpowiedź nie pasuje do zapytania');
      return out.body;
    },
  };

  // Sufiksy publiczne: subdomeny pod nimi należą do różnych właścicieli (mojprojekt.github.io ≠ obcy.github.io).
  // Ta sama lista co w shared/…/data/Domains.kt.
  const PUBLIC = new Set([
    'github.io', 'gitlab.io', 'bitbucket.io', 'codeberg.page', 'vercel.app', 'netlify.app', 'herokuapp.com',
    'pages.dev', 'workers.dev', 'r2.dev', 'trycloudflare.com', 'web.app', 'firebaseapp.com', 'appspot.com',
    'blogspot.com', 'wordpress.com', 'azurewebsites.net', 'azurestaticapps.net', 'cloudapp.net', 'cloudfront.net',
    'amazonaws.com', 'elasticbeanstalk.com', 'onrender.com', 'fly.dev', 'glitch.me', 'repl.co', 'replit.app',
    'ngrok.io', 'ngrok.app', 'ngrok-free.app', 'duckdns.org', 'no-ip.org', 'ddns.net', 'myshopify.com',
    'wixsite.com', 'squarespace.com', 'weebly.com', 'tumblr.com', 'substack.com', 'notion.site', 'translate.goog',
    'sharepoint.com', 'webflow.io', 'carrd.co', 'neocities.org', 'surge.sh', 'readthedocs.io', 'pythonanywhere.com',
    'eu.org', 'us.org', 'uk.net', 'gb.net', 'eu.com', 'us.com', 'br.com', 'cn.com', 'de.com', 'ru.com', 'sa.com',
    'za.com', 'se.net', 'com.pl', 'net.pl', 'org.pl', 'info.pl', 'biz.pl', 'edu.pl', 'gov.pl', 'mil.pl', 'priv.pl',
    'nom.pl', 'art.pl', 'atm.pl', 'auto.pl', 'gmina.pl', 'gsm.pl', 'mail.pl', 'media.pl', 'miasta.pl', 'pc.pl',
    'powiat.pl', 'realestate.pl', 'rel.pl', 'sex.pl', 'shop.pl', 'sklep.pl', 'sos.pl', 'tm.pl', 'tourism.pl',
    'travel.pl', 'turystyka.pl', 'agro.pl', 'waw.pl', 'warszawa.pl', 'krakow.pl', 'wroc.pl', 'wroclaw.pl',
    'poznan.pl', 'lodz.pl', 'gda.pl', 'gdansk.pl', 'gdynia.pl', 'sopot.pl', 'szczecin.pl', 'katowice.pl',
    'lublin.pl', 'bialystok.pl', 'olsztyn.pl', 'torun.pl', 'bydgoszcz.pl', 'rzeszow.pl', 'kielce.pl', 'opole.pl',
    'gliwice.pl', 'zgora.pl', 'radom.pl', 'czest.pl', 'slask.pl', 'malopolska.pl', 'mazowsze.pl', 'pomorskie.pl',
    'wielkopolska.pl', 'warmia.pl', 'podlasie.pl', 'bielsko.pl', 'tychy.pl', 'elk.pl', 'nysa.pl', 'pila.pl',
    'lubin.pl',
  ]);
  // Tu każdy poziom subdomeny ma innego właściciela (kubeł.s3.region.amazonaws.com) - liczy się cały adres.
  const WHOLE_HOST = ['amazonaws.com'];
  const CC_SECOND = new Set([
    'com', 'co', 'org', 'net', 'gov', 'edu', 'ac', 'or', 'ne', 'go', 'gob', 'mil', 'nic', 'ltd', 'plc', 'sch', 'nhs',
    'gen', 'biz', 'info', 'nom', 'int',
  ]);

  /** Domena rejestrowalna - jak Domains.registrable() w Kotlinie. */
  Ostoja.registrableDomain = function (host) {
    const h = String(host || '').toLowerCase().trim().replace(/\.+$/, '').replace(/^www\./, '');
    if (!h || /^\d{1,3}(\.\d{1,3}){3}$/.test(h) || h.startsWith('[') || !h.includes('.')) return h;
    if (WHOLE_HOST.some((w) => h.endsWith('.' + w))) return h;
    const parts = h.split('.').filter(Boolean);
    let suffix = '';
    for (const s of PUBLIC) if ((h === s || h.endsWith('.' + s)) && s.length > suffix.length) suffix = s;
    const keep = suffix ? suffix.split('.').length + 1
      : parts.length >= 3 && parts[parts.length - 1].length === 2 && CC_SECOND.has(parts[parts.length - 2]) ? 3 : 2;
    return parts.slice(-Math.min(keep, parts.length)).join('.');
  };

  /** Czy strona `host` należy do domeny wpisu (ochrona przed wpisaniem hasła na fałszywej stronie). */
  Ostoja.hostMatches = function (host, domain) {
    host = String(host || '').toLowerCase().replace(/^www\./, '');
    domain = String(domain || '').toLowerCase();
    if (!host || !domain) return false;
    return host === domain || host.endsWith('.' + domain);
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = Ostoja;
  else root.Ostoja = Ostoja;
})(typeof self !== 'undefined' ? self : globalThis);
