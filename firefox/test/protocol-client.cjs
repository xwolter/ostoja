// Klient testowy: prawdziwy firefox/lib/protocol.js w Node łączy się z ExtBridge (desktop/ExtBridgeTest).
// Wypisuje kod parowania, czeka na zatwierdzenie, potem robi zapytania jak tło rozszerzenia.
if (process.env.OSTOJA_BASE) globalThis.OSTOJA_BASE = process.env.OSTOJA_BASE;
const Ostoja = require('../lib/protocol.js');

(async () => {
  const p = await Ostoja.pair('Firefox (test)');
  console.log('CODE ' + p.code);
  for (let i = 0; i < 50; i++) {
    const s = await Ostoja.pairStatus(p.id);
    if (s === 'ok') break;
    if (s !== 'waiting') throw new Error('pair ' + s);
    await new Promise((r) => setTimeout(r, 100));
  }
  const session = { id: p.id, key: p.key };
  const state = await Ostoja.call(session, 'state');
  const list = await Ostoja.call(session, 'list');
  const creds = await Ostoja.call(session, 'creds', { id: 'site' });
  const otp = await Ostoja.call(session, 'otp', { id: 'site' });
  const url = 'http://localhost:8765/login.html';
  const same = await Ostoja.call(session, 'check', { url, username: 'anna', password: 'Sekret-123!' });
  const changed = await Ostoja.call(session, 'check', { url, username: 'anna', password: 'Nowe-haslo-1' });
  const fresh = await Ostoja.call(session, 'check', { url: 'https://www.allegro.pl/logowanie', username: 'jan', password: 'x' });
  const saved = await Ostoja.call(session, 'save', { url: 'https://www.allegro.pl/logowanie', username: 'jan', password: 'Allegro-2026!' });
  const updated = await Ostoja.call(session, 'save', { id: changed.id, url, username: 'anna', password: 'Nowe-haslo-1' });
  // Zrzut karty z kodem QR (jak captureVisibleTab) → aplikacja dodaje konto 2FA; drugi raz - „już jest”.
  const image = require('fs').readFileSync(process.env.QR_JPEG).toString('base64');
  const qr = [await Ostoja.call(session, 'otp-add', { image, url: 'https://github.com/settings/two_factor_authentication/setup/intro' }),
    await Ostoja.call(session, 'otp-add', { image, url: 'https://github.com/settings/two_factor_authentication/setup/intro' })];
  const loginUrl = [
    await Ostoja.call(session, 'login-url', { id: 'shop', url: 'https://sklep.example.pl/logowanie?ref=home' }),
    await Ostoja.call(session, 'login-url', { id: 'shop', url: 'https://sklep.example.pl/inna' }),
    await Ostoja.call(session, 'login-url', { id: 'gh', url: 'https://evil.example.com/login' }),
  ].map((x) => x.ok);
  console.log('RESULT ' + JSON.stringify({ state, list, creds, otp, check: [same.state, changed.state, fresh.state, fresh.title], saved, updated, qr, loginUrl }));
})().catch((e) => { console.log('ERROR ' + e.message); process.exit(1); });
