/*
 * Ostoja - tło rozszerzenia.
 *
 * Trzyma połączenie z aplikacją Ostoja na Windows (długie zapytanie „wait”, przez które aplikacja
 * i telefon wysyłają polecenia „otwórz i zaloguj”), zarządza zadaniami logowania w kartach
 * i odpowiada okienku. Hasła nie są przechowywane: przychodzą z aplikacji na jedno logowanie
 * i znikają z pamięci po jego zakończeniu (najpóźniej po 5 minutach).
 */
'use strict';

const JOB_TTL = 300000;     // logowanie z passkeyem i 2FA potrafi trwać dłużej niż 2 min
let session = null;          // {id, key, computerName}
let status = 'starting';     // starting | offline | unpaired | locked | ready
let appState = null;         // {locked, paired, computerName, phoneName, accent, icons}
let items = [];              // wpisy bez haseł: {id, title, user, url, domain, has2fa}
let itemsAt = 0;
let pairing = null;          // {id, key, code, computerName, state}
const jobs = new Map();      // tabId → zadanie logowania
const pendingSave = new Map(); // tabId → złapane logowanie czekające na „Zapisać hasło?”
const lastUser = new Map();  // tabId → login wpisany w pierwszym kroku (logowanie dwuetapowe)

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const hostOf = (url) => { try { return new URL(url).hostname.toLowerCase(); } catch (e) { return ''; } };
const pagePath = (url) => { try { const u = new URL(url); return u.origin + u.pathname; } catch (e) { return ''; } };

async function loadSession() {
  const s = await browser.storage.local.get('session');
  session = s.session || null;
}

async function saveSession(s) {
  session = s;
  if (s) await browser.storage.local.set({ session: s });
  else await browser.storage.local.remove('session');
}

function setStatus(s) {
  if (status === s) return;
  status = s;
  updateBadge();
}

async function handleError(e) {
  if (e && e.code === 'unpaired') { await saveSession(null); setStatus('unpaired'); return; }
  setStatus(e && e.code === 'offline' ? 'offline' : status === 'starting' ? 'offline' : status);
}

// --- dane z aplikacji ---

async function refresh(force = false) {
  if (!session) { setStatus('unpaired'); return; }
  if (!force && Date.now() - itemsAt < 30000 && status === 'ready') return;
  try {
    appState = await Ostoja.call(session, 'state');
    const list = await Ostoja.call(session, 'list');
    items = list.items || [];
    itemsAt = Date.now();
    setStatus(list.locked ? 'locked' : 'ready');
  } catch (e) {
    await handleError(e);
  }
}

function matchesFor(url) {
  const host = hostOf(url);
  if (!host) return [];
  return items.filter((i) => Ostoja.hostMatches(host, i.domain));
}

async function updateBadge() {
  try {
    const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
    const n = status === 'ready' && tab ? matchesFor(tab.url).length : 0;
    const text = status === 'locked' ? '🔒' : n ? String(n) : '';
    await browser.browserAction.setBadgeText({ text, tabId: tab ? tab.id : undefined });
    await browser.browserAction.setBadgeBackgroundColor({ color: ['#3E91FF', '#2FA57A', '#7A6CF0', '#E0704A'][(appState && appState.accent) || 0] });
  } catch (e) { /* karta systemowa */ }
}

// --- pętla poleceń z aplikacji i telefonu ---

async function loop() {
  for (;;) {
    if (!session) { setStatus('unpaired'); await sleep(2000); continue; }
    try {
      const r = await Ostoja.call(session, 'wait');
      if (status !== 'ready' || r.locked) await refresh(true);
      setStatus(r.locked ? 'locked' : 'ready');
      for (const c of r.commands || []) {
        if (c.type === 'login') openAndLogin(c.loginId).catch((e) => notify('Nie udało się zalogować', e.message));
      }
    } catch (e) {
      await handleError(e);
      await sleep(status === 'offline' ? 5000 : 3000);
    }
  }
}

function notify(title, message) {
  browser.notifications.create({ type: 'basic', iconUrl: 'icons/icon-96.png', title, message }).catch(() => {});
}

// --- logowanie ---

async function creds(id) {
  const c = await Ostoja.call(session, 'creds', { id });
  if (!c.ok) {
    if (c.error === 'locked') { setStatus('locked'); throw new Error('Sejf w aplikacji jest zablokowany - potwierdź logowanie na telefonie'); }
    throw new Error('Tego wpisu nie ma już w sejfie');
  }
  return c;
}

function startJob(tabId, c, id) {
  jobs.set(tabId, {
    id, title: c.title, domain: c.domain, username: c.username, password: c.password, hasOtp: c.hasOtp,
    secure: !/^http:/i.test(c.url || ''),
    phase: 'creds', started: Date.now(),
  });
  setTimeout(() => {
    const j = jobs.get(tabId);
    if (j && Date.now() - j.started >= JOB_TTL) jobs.delete(tabId);
  }, JOB_TTL + 1000);
}

/** Wybrana strona otwiera się w nowej karcie, a skrypt na stronie loguje się sam. */
async function openAndLogin(id) {
  const c = await creds(id);
  if (!c.url) throw new Error('Wpis nie ma adresu strony');
  const tab = await browser.tabs.create({ url: c.url, active: true });
  startJob(tab.id, c, id);
  browser.windows.update(tab.windowId, { focused: true }).catch(() => {});
  return { ok: true };
}

/** Wypełnienie na stronie, która jest już otwarta (sekcja „Na tej stronie” w okienku). */
async function fillHere(id) {
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
  if (!tab) throw new Error('Brak otwartej karty');
  const c = await creds(id);
  if (!Ostoja.hostMatches(hostOf(tab.url), c.domain)) throw new Error(`Ten wpis jest dla ${c.domain}, a karta to ${hostOf(tab.url)}`);
  startJob(tab.id, c, id);
  await browser.tabs.sendMessage(tab.id, { type: 'job' }).catch(() => {});
  return { ok: true };
}

// Strona z zadaniem przeładowała się (logowanie w kilku krokach) - skrypt na nowej stronie sam zapyta o zadanie,
// a na wszelki wypadek przypominamy mu o nim.
browser.tabs.onUpdated.addListener((tabId, info) => {
  if (info.status === 'complete') {
    if (jobs.has(tabId)) browser.tabs.sendMessage(tabId, { type: 'job' }).catch(() => {});
    updateBadge();
  }
});
browser.tabs.onActivated.addListener(() => updateBadge());
browser.tabs.onRemoved.addListener((tabId) => { jobs.delete(tabId); pendingSave.delete(tabId); lastUser.delete(tabId); });

// --- zapisywanie haseł ---

async function neverSites() {
  return (await browser.storage.local.get('never')).never || [];
}

/** Strona wysłała formularz logowania: sprawdzamy w aplikacji, czy to nowe konto albo zmienione hasło. */
async function captured(tabId, c) {
  if (!session || status !== 'ready' || jobs.has(tabId)) return;
  const host = hostOf(c.url);
  if (!host || (await neverSites()).includes(Ostoja.registrableDomain(host))) return;
  const username = c.username || (lastUser.get(tabId) && lastUser.get(tabId).host === host ? lastUser.get(tabId).username : '');
  let r;
  try { r = await Ostoja.call(session, 'check', { url: c.url, username, password: c.password }); } catch (e) { return; }
  if (r.state !== 'new' && r.state !== 'changed') return;
  pendingSave.set(tabId, { url: c.url, host, username, password: c.password, state: r.state, id: r.id, title: r.title, at: Date.now() });
  // Strona bez przeładowania (SPA) - pokazujemy pytanie od razu; po przeładowaniu zapyta nowa strona.
  setTimeout(() => browser.tabs.sendMessage(tabId, { type: 'offerSave' }).catch(() => {}), 1200);
  setTimeout(() => { const p = pendingSave.get(tabId); if (p && Date.now() - p.at > 120000) pendingSave.delete(tabId); }, 121000);
}

async function save(entry) {
  const r = await Ostoja.call(session, 'save', entry);
  if (!r.ok) throw new Error(r.error === 'locked' ? 'Sejf w aplikacji jest zablokowany' : 'Nie udało się zapisać');
  itemsAt = 0;
  refresh(true);
  return r;
}

// --- kody 2FA do wpisania ---

// Domena → ostatnio wpisany kod. Ten sam kod drugi raz serwis odrzuci („already been used”), więc wtedy czekamy na następny.
const usedOtp = new Map();

async function freshOtp(id, url) {
  const r = await Ostoja.call(session, 'otp', { id });
  if (!r.ok) return r;
  const d = Ostoja.registrableDomain(hostOf(url));
  if (usedOtp.get(d) === r.code) return { ok: false, wait: Math.max(1, r.remaining || 1) + 1 };
  usedOtp.set(d, r.code);
  return r;
}

// --- kod 2FA z kodu QR na stronie (np. GitHub → Settings → Two-factor authentication) ---

/** Zrzut widocznej części karty idzie do aplikacji, która odczytuje kod QR i dodaje konto 2FA. */
async function addOtpFromTab(tab) {
  if (!session) throw new Error('Najpierw połącz rozszerzenie z aplikacją Ostoja');
  const shot = await browser.tabs.captureVisibleTab(tab.windowId, { format: 'jpeg', quality: 95 });
  const r = await Ostoja.call(session, 'otp-add', { image: shot.slice(shot.indexOf(',') + 1), url: tab.url });
  if (r.ok) return { ok: true, title: r.title };
  throw new Error({
    locked: 'Sejf Ostoi jest zablokowany - odblokuj go w okienku Ostoi',
    noqr: 'Nie widzę kodu QR - przewiń stronę tak, żeby kod był cały widoczny',
    nototp: 'Ten kod QR nie jest kodem 2FA',
    exists: `Kod 2FA dla ${r.title} już jest w Ostoi`,
  }[r.error] || 'Nie udało się dodać kodu 2FA');
}

// --- parowanie ---

async function startPairing() {
  const p = await Ostoja.pair('Firefox');
  pairing = { ...p, state: 'waiting' };
  (async () => {
    const until = Date.now() + 120000;
    while (pairing && pairing.id === p.id && Date.now() < until) {
      await sleep(1000);
      let st;
      try { st = await Ostoja.pairStatus(p.id); } catch (e) { continue; }
      if (st === 'ok') {
        await saveSession({ id: p.id, key: p.key, computerName: p.computerName });
        pairing.state = 'ok';
        await refresh(true);
        return;
      }
      if (st === 'rejected' || st === 'none') { pairing.state = 'rejected'; return; }
    }
    if (pairing && pairing.id === p.id && pairing.state === 'waiting') pairing.state = 'expired';
  })();
  return { code: p.code, computerName: p.computerName };
}

// --- schowek: skopiowane hasło znika po 30 s, jeśli nic innego nie zostało skopiowane ---

function scheduleClipboardClear(value) {
  setTimeout(async () => {
    try {
      if ((await navigator.clipboard.readText()) === value) await navigator.clipboard.writeText('');
    } catch (e) { /* brak dostępu do schowka */ }
  }, 30000);
}

// --- wiadomości z okienka i ze stron ---

// Wiadomości tylko z okienka rozszerzenia. Skrypt na stronie (sender.tab) nie dostanie haseł z sejfu, listy wpisów
// ani nie rozłączy rozszerzenia, nawet gdyby strona znalazła sposób, żeby nim sterować.
const POPUP_ONLY = new Set(['status', 'list', 'pair', 'pairState', 'unpair', 'login', 'fill', 'secret', 'add', 'generate', 'copied', 'unlock', 'unlockStatus']);

/** Strona bez szyfrowania (http://) - poza adresami lokalnymi, na których testuje się własne strony. */
function insecure(url) {
  try {
    const u = new URL(url);
    return u.protocol === 'http:' && !['localhost', '127.0.0.1', '[::1]'].includes(u.hostname);
  } catch (e) { return true; }
}

browser.runtime.onMessage.addListener((m, sender) => {
  const fromTab = sender.tab && sender.tab.id;
  if (!m || sender.id !== browser.runtime.id) return undefined;
  if (POPUP_ONLY.has(m.type) && (sender.tab || !String(sender.url || '').startsWith(browser.runtime.getURL('')))) {
    return Promise.resolve({ error: 'Niedozwolone' });
  }
  switch (m.type) {
    // okienko
    case 'status':
      return refresh(m.force).then(() => ({ status, appState, computerName: session && session.computerName, pairing }));
    case 'list':
      return refresh(m.force).then(async () => {
        const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
        return { status, items, here: tab ? matchesFor(tab.url).map((i) => i.id) : [], host: tab ? hostOf(tab.url) : '', tabUrl: tab ? tab.url : '' };
      });
    case 'pair':
      return startPairing().catch((e) => ({ error: e.message }));
    case 'pairState':
      return Promise.resolve(pairing);
    case 'unpair':
      return saveSession(null).then(() => { items = []; setStatus('unpaired'); return { ok: true }; });
    case 'login':
      return openAndLogin(m.id).catch((e) => ({ error: e.message }));
    case 'fill':
      return fillHere(m.id).catch((e) => ({ error: e.message }));
    case 'secret':
      return (async () => {
        try {
          if (m.what === 'otp') {
            const r = await Ostoja.call(session, 'otp', { id: m.id });
            return r.ok ? { value: r.code } : { error: 'Brak kodu 2FA' };
          }
          const c = await creds(m.id);
          return { value: m.what === 'username' ? c.username : c.password };
        } catch (e) { return { error: e.message }; }
      })();
    case 'addOtpTab':
      return (async () => {
        const [tab] = fromTab != null ? [sender.tab] : await browser.tabs.query({ active: true, currentWindow: true });
        return addOtpFromTab(tab);
      })().catch((e) => ({ error: e.message }));
    case 'add':
      return save({ title: m.title, url: m.url, username: m.username, password: m.password, note: m.note || '' }).catch((e) => ({ error: e.message }));
    case 'generate': {
      // Silne hasło: 20 znaków, bez mylących (l/I/1/O/0), co najmniej po jednym z każdej grupy.
      const sets = ['abcdefghijkmnopqrstuvwxyz', 'ABCDEFGHJKLMNPQRSTUVWXYZ', '23456789', '!@#$%^&*-_=+?'];
      const all = sets.join('');
      const rnd = (n) => crypto.getRandomValues(new Uint32Array(1))[0] % n;
      const out = Array.from({ length: m.length || 20 }, () => all[rnd(all.length)]);
      sets.forEach((s, i) => { out[(rnd(out.length) + i * 5) % out.length] = s[rnd(s.length)]; });
      return Promise.resolve({ value: out.join('') });
    }
    case 'copied':
      if (m.sensitive) scheduleClipboardClear(m.value);
      return Promise.resolve({ ok: true });
    case 'unlock':
      return Ostoja.call(session, 'unlock').catch((e) => ({ state: 'error', error: e.message }));
    case 'unlockStatus':
      return Ostoja.call(session, 'unlock-status').then(async (r) => { if (r.state === 'ok') await refresh(true); return r; }).catch((e) => ({ state: 'error' }));

    // skrypt na stronie
    case 'job?': {
      const j = fromTab != null && jobs.get(fromTab);
      if (!j || Date.now() - j.started > JOB_TTL) return Promise.resolve(null);
      if (!Ostoja.hostMatches(hostOf(sender.tab.url), j.domain)) {
        // Przekierowanie na inną domenę (SSO, phishing) - hasło nie wychodzi z tła.
        return Promise.resolve({ mismatch: true, domain: j.domain, title: j.title });
      }
      // Wpis z https://, a strona po http:// (np. przechwycone połączenie) - hasło poszłoby otwartym tekstem.
      if (j.secure && insecure(sender.tab.url)) return Promise.resolve({ insecure: true, title: j.title });
      return Promise.resolve({ title: j.title, username: j.username, password: j.password, hasOtp: j.hasOtp, phase: j.phase });
    }
    case 'jobPhase': {
      const j = jobs.get(fromTab);
      if (j) j.phase = m.phase;
      return Promise.resolve({ ok: true });
    }
    case 'jobHop': {
      // Na stronie nie ma formularza, tylko link „Zaloguj się” - najwyżej dwa przejścia na zadanie.
      const j = jobs.get(fromTab);
      if (!j) return Promise.resolve(false);
      j.hops = (j.hops || 0) + 1;
      j.hopFrom = pagePath(sender.tab.url);
      return Promise.resolve(j.hops <= 2);
    }
    case 'jobLoginUrl': {
      // Formularz znalazł się dopiero po przejściu z domeny - aplikacja zapamięta tę stronę we wpisie.
      const j = jobs.get(fromTab);
      // Okienko logowania na tej samej stronie (bez przejścia) - adres wpisu zostaje.
      if (!j || !j.hops || j.urlSaved || pagePath(sender.tab.url) === j.hopFrom || !Ostoja.hostMatches(hostOf(sender.tab.url), j.domain)) return Promise.resolve({ ok: false });
      j.urlSaved = true;
      return Ostoja.call(session, 'login-url', { id: j.id, url: m.url })
        .then((r) => { if (r.ok) refresh(true).catch(() => {}); return r; })
        .catch(() => ({ ok: false }));
    }
    case 'jobOtp': {
      const j = jobs.get(fromTab);
      if (!j || !Ostoja.hostMatches(hostOf(sender.tab.url), j.domain) || (j.secure && insecure(sender.tab.url))) return Promise.resolve({ ok: false });
      return freshOtp(j.id, sender.tab.url).catch(() => ({ ok: false }));
    }
    case 'otpHere':
      // Strona pyta o kod 2FA, choć logowania nie zaczęła Ostoja (hasło wpisane ręcznie, autouzupełnianie Firefoksa).
      return (async () => {
        if (!session) return { error: 'unpaired' };
        await refresh();
        if (status === 'locked') return { error: 'locked' };
        if (insecure(sender.tab.url)) return null;
        const m = matchesFor(sender.tab.url).sort((a, b) => b.has2fa - a.has2fa);
        for (const i of m) {
          const r = await freshOtp(i.id, sender.tab.url).catch(() => null);
          if (r && r.wait) return { wait: r.wait, title: i.title };
          if (r && r.ok) return { code: r.code, title: i.title };
        }
        return null;
      })();
    case 'userSeen':
      if (fromTab != null && m.username) lastUser.set(fromTab, { host: hostOf(sender.tab.url), username: m.username });
      return Promise.resolve({ ok: true });
    case 'captured':
      if (fromTab != null) captured(fromTab, { url: sender.tab.url, username: m.username, password: m.password });
      return Promise.resolve({ ok: true });
    case 'pendingSave?': {
      const p = pendingSave.get(fromTab);
      if (!p || p.shown || Date.now() - p.at > 120000 || hostOf(sender.tab.url) !== p.host && !Ostoja.hostMatches(hostOf(sender.tab.url), Ostoja.registrableDomain(p.host))) return Promise.resolve(null);
      p.shown = true;
      return Promise.resolve({ state: p.state, title: p.title, username: p.username, host: p.host });
    }
    case 'saveDecision': {
      const p = pendingSave.get(fromTab);
      pendingSave.delete(fromTab);
      if (!p) return Promise.resolve({ ok: false });
      if (m.decision === 'never') {
        return neverSites().then((n) => browser.storage.local.set({ never: [...new Set([...n, Ostoja.registrableDomain(p.host)])] })).then(() => ({ ok: true }));
      }
      if (m.decision !== 'save') return Promise.resolve({ ok: true });
      return save({ id: p.state === 'changed' ? p.id : null, title: p.title, url: p.url, username: p.username, password: p.password })
        .then((r) => ({ ok: true, title: r.title }))
        .catch((e) => ({ ok: false, error: e.message }));
    }
    case 'jobDone':
      jobs.delete(fromTab);
      return Promise.resolve({ ok: true });
  }
  return undefined;
});

browser.commands.onCommand.addListener(async (cmd) => {
  // Ctrl+Shift+L: wypełnij pierwszym pasującym wpisem na tej stronie.
  if (cmd !== 'fill-here') return;
  await refresh();
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
  const m = tab ? matchesFor(tab.url) : [];
  if (m.length) fillHere(m[0].id).catch((e) => notify('Ostoja', e.message));
  else notify('Ostoja', 'Brak zapisanych haseł dla tej strony');
});

(async () => {
  await loadSession();
  await refresh(true);
  loop();
})();
