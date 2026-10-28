/* Ostoja - okienko rozszerzenia: lista wpisów, „Otwórz i zaloguj”, parowanie i odblokowanie. */
'use strict';

const $ = (s) => document.querySelector(s);
const view = $('#view');
const ACCENTS = ['#3E91FF', '#2FA57A', '#7A6CF0', '#E0704A'];
const send = (m) => browser.runtime.sendMessage(m);
const esc = (s) => String(s || '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const ICON = {
  search: '<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="M20.5 20.5L16 16"/></svg>',
  key: '<svg viewBox="0 0 24 24"><circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M17 6l3 3M15 8l2 2"/></svg>',
  user: '<svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 20c1.5-3.5 4.5-5 8-5s6.5 1.5 8 5"/></svg>',
  clock: '<svg viewBox="0 0 24 24"><circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2M10 2.5h4"/></svg>',
  go: '<svg viewBox="0 0 24 24"><path d="M5 12h13M13 6l6 6-6 6"/></svg>',
  plug: '<svg viewBox="0 0 24 24"><path d="M9 3v5M15 3v5M6 8h12v3a6 6 0 0 1-12 0zM12 17v4"/></svg>',
  lock: '<svg viewBox="0 0 24 24"><rect x="5" y="10.5" width="14" height="10" rx="2.6"/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"/></svg>',
  laptop: '<svg viewBox="0 0 24 24"><path d="M5 5h14a1 1 0 0 1 1 1v9H4V6a1 1 0 0 1 1-1zM2 18h20"/></svg>',
  plus: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
  regen: '<svg viewBox="0 0 24 24"><path d="M20 11a8 8 0 0 0-14.5-4.5L4 8M4 4v4h4M4 13a8 8 0 0 0 14.5 4.5L20 16M20 20v-4h-4"/></svg>',
  eye: '<svg viewBox="0 0 24 24"><path d="M2 12c2-4.5 5.6-7 10-7s8 2.5 10 7c-2 4.5-5.6 7-10 7s-8-2.5-10-7z"/><circle cx="12" cy="12" r="3"/></svg>',
  qr: '<svg viewBox="0 0 24 24"><path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2v2h-2zM18 18h2v2h-2zM14 18h2M18 14h2"/></svg>',
  shield: '<svg viewBox="0 0 24 24"><path d="M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6z"/><path d="M9 12l2 2 4-4"/></svg>',
};

let state = null;
let data = { items: [], here: [], host: '' };
let query = '';
let sel = 0;
let icons = true;

function toast(t) {
  const el = $('#toast');
  el.textContent = t;
  el.classList.add('on');
  clearTimeout(toast.t);
  toast.t = setTimeout(() => el.classList.remove('on'), Math.max(1800, t.length * 60));
}

function setConn(text, cls) {
  $('#conn').innerHTML = `<i class="dot ${cls || ''}"></i><em>${esc(text)}</em>`;
}

function stateScreen(icon, title, text, button, onClick, extra = '') {
  view.innerHTML = `<div class="state"><div class="badge">${ICON[icon]}</div><h2>${esc(title)}</h2><p>${text}</p>${extra}${button ? `<button class="btn" id="act">${esc(button)}</button>` : ''}</div>`;
  if (button) $('#act').addEventListener('click', onClick);
}

// --- ekrany ---

function showOffline() {
  setConn('Aplikacja nie działa', '');
  stateScreen('laptop', 'Uruchom Ostoję na komputerze',
    'Rozszerzenie bierze hasła z aplikacji Ostoja na Windows - dzięki temu nic nie jest zapisywane w przeglądarce. Otwórz ją (Start → Ostoja) i spróbuj ponownie.',
    'Spróbuj ponownie', () => init(true));
}

function showUnpaired() {
  setConn('Niepołączone', '');
  stateScreen('plug', 'Połącz z aplikacją Ostoja',
    'Wybrana strona otworzy się i zaloguje sama - login, hasło i kod 2FA. Połączenie jest szyfrowane, a hasło trafia do przeglądarki dopiero w chwili logowania.',
    'Połącz', startPair);
}

async function startPair() {
  const r = await send({ type: 'pair' });
  if (!r || r.error) { toast(r && r.error ? r.error : 'Nie udało się połączyć'); return; }
  stateScreen('shield', 'Potwierdź w aplikacji',
    `W oknie Ostoi na ${esc(r.computerName)} kliknij „Połącz”, jeśli widzisz ten sam kod:`,
    null, null,
    `<div class="code">${r.code.slice(0, 3)} ${r.code.slice(3)}</div><div class="wait"><span class="spinner"></span>Czekam na potwierdzenie…</div>`);
  const until = Date.now() + 120000;
  while (Date.now() < until) {
    await new Promise((res) => setTimeout(res, 800));
    const p = await send({ type: 'pairState' });
    if (!p) break;
    if (p.state === 'ok') { toast('Połączono z ' + p.computerName); return init(true); }
    if (p.state === 'rejected' || p.state === 'expired') {
      stateScreen('plug', 'Nie połączono', 'Połączenie zostało odrzucone w aplikacji albo wygasło.', 'Spróbuj ponownie', startPair);
      return;
    }
  }
}

function showLocked() {
  setConn(`${state.appState ? state.appState.computerName : 'Komputer'} · zablokowany`, 'warn');
  stateScreen('lock', 'Sejf zablokowany',
    'Co 7 dni Ostoja na komputerze prosi o potwierdzenie na telefonie. Kliknij poniżej i wybierz na telefonie liczbę, która pojawi się tutaj.',
    'Potwierdź na telefonie', startUnlock);
}

async function startUnlock() {
  let r = await send({ type: 'unlock' });
  if (r.state === 'ok') return init(true);
  if (r.state !== 'waiting') { toast('Nie udało się wysłać prośby'); return; }
  const phone = state.appState && state.appState.phoneName ? esc(state.appState.phoneName) : 'telefonie';
  stateScreen('lock', 'Potwierdź na telefonie', `Otwórz powiadomienie Ostoi na ${phone} i wybierz tę liczbę:`, null, null,
    `<div class="num"><b>${r.code}</b></div><div class="wait" id="left"></div>`);
  while (true) {
    const left = Math.max(0, Math.round((r.expires - Date.now()) / 1000));
    const el = $('#left');
    if (el) el.textContent = `Prośba wygaśnie za ${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`;
    await new Promise((res) => setTimeout(res, 1000));
    const s = await send({ type: 'unlockStatus' });
    if (s.state === 'ok') { toast('Sejf odblokowany na 7 dni'); return init(true); }
    if (s.state !== 'waiting') { stateScreen('lock', 'Nie potwierdzono', 'Prośba została odrzucona albo wygasła.', 'Spróbuj ponownie', startUnlock); return; }
  }
}

// --- lista ---

function fav(item) {
  const letter = esc((item.title.match(/[\p{L}\p{N}]/u) || ['?'])[0].toUpperCase());
  if (!icons || !item.domain) return `<div class="fav">${letter}</div>`;
  return `<div class="fav"><img src="https://www.google.com/s2/favicons?domain=${encodeURIComponent(item.domain)}&sz=64" alt="" data-l="${letter}"></div>`;
}

function rowHtml(item, i, here) {
  return `<div class="row" data-id="${esc(item.id)}" data-i="${i}" style="animation-delay:${Math.min(i * 30, 300)}ms">
    ${fav(item)}
    <div class="txt"><b>${esc(item.title)}</b><span>${esc(item.user || item.domain)}</span></div>
    ${item.has2fa ? '<span class="tag">2FA</span>' : ''}
    <div class="acts">
      <button class="icon" data-copy="username" title="Kopiuj login">${ICON.user}</button>
      <button class="icon" data-copy="password" title="Kopiuj hasło">${ICON.key}</button>
      ${item.has2fa ? `<button class="icon" data-copy="otp" title="Kopiuj kod 2FA">${ICON.clock}</button>` : ''}
    </div>
    ${here ? '<button class="fill" data-fill="1">Wypełnij</button>' : `<button class="go" title="Otwórz i zaloguj">${ICON.go}</button>`}
  </div>`;
}

function filtered() {
  const q = query.trim().toLowerCase();
  const list = data.items.slice().sort((a, b) => a.title.localeCompare(b.title, 'pl'));
  if (!q) return list;
  return list.filter((i) => [i.title, i.user, i.domain, i.url].some((v) => String(v || '').toLowerCase().includes(q)));
}

function renderList() {
  const all = filtered();
  const hereIds = new Set(data.here);
  const here = query ? [] : all.filter((i) => hereIds.has(i.id));
  const rest = query ? all : all.filter((i) => !hereIds.has(i.id));
  const order = [...here, ...rest];
  sel = Math.min(sel, Math.max(0, order.length - 1));
  let i = 0;
  let html = '';
  if (here.length) html += `<div class="label">Na tej stronie · ${esc(data.host)}</div><div class="card">${here.map((it) => rowHtml(it, i++, true)).join('')}</div>`;
  if (rest.length) html += `<div class="label">${query ? 'Wyniki' : 'Otwórz i zaloguj'}</div><div class="card">${rest.map((it) => rowHtml(it, i++, false)).join('')}</div>`;
  if (!query && /^https?:/.test(data.tabUrl || '')) {
    const addPw = here.length ? '' : `<div class="row addhere" style="animation-delay:0ms"><div class="fav">${ICON.plus}</div><div class="txt"><b>Dodaj hasło dla ${esc(data.host)}</b><span>Zapisze się w Ostoi i na telefonie</span></div></div>`;
    const addOtp = `<div class="row addotp" style="animation-delay:0ms"><div class="fav">${ICON.qr}</div><div class="txt"><b>Dodaj kod 2FA z tej strony</b><span>Odczyta kod QR widoczny na stronie</span></div></div>`;
    html = `<div class="card">${addPw}${addOtp}</div>` + html;
  }
  if (!order.length) html += `<div class="card"><div class="empty">${query ? 'Nic nie pasuje do „' + esc(query) + '”' : 'Sejf jest pusty - dodaj wpis na telefonie'}</div></div>`;
  html += `<div class="hint"><kbd>↑</kbd> <kbd>↓</kbd> wybór · <kbd>Enter</kbd> zaloguj · <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>L</kbd> wypełnij stronę</div>`;
  $('#list').innerHTML = html;
  $('#list').querySelectorAll('.row').forEach((r) => r.classList.toggle('sel', Number(r.dataset.i) === sel));
  $('#list').querySelectorAll('.fav img').forEach((img) => img.addEventListener('error', () => { img.parentElement.textContent = img.dataset.l; }));
  renderList.order = order;
  renderList.here = hereIds;
}

function showList() {
  setConn(`${state.computerName || state.appState.computerName} · połączono`, 'on');
  view.innerHTML = `<div class="search">${ICON.search}<input id="q" placeholder="Szukaj w sejfie" autocomplete="off"></div><div id="list"></div>`;
  const q = $('#q');
  q.value = query;
  q.addEventListener('input', () => { query = q.value; sel = 0; renderList(); });
  q.focus();
  renderList();
}

async function addOtp() {
  toast('Szukam kodu QR na stronie…');
  const r = await send({ type: 'addOtpTab' });
  if (!r || r.error) { toast(r ? r.error : 'Błąd'); return; }
  toast(`Dodano kod 2FA · ${r.title}`);
  init(true);
}

async function act(id, fill) {
  const r = await send({ type: fill ? 'fill' : 'login', id });
  if (r && r.error) { toast(r.error); return; }
  window.close();
}

async function copy(id, what) {
  const r = await send({ type: 'secret', id, what });
  if (!r || r.error) { toast(r ? r.error : 'Błąd'); return; }
  await navigator.clipboard.writeText(r.value);
  send({ type: 'copied', value: r.value, sensitive: what !== 'username' });
  toast(what === 'otp' ? `Skopiowano kod ${r.value.slice(0, 3)} ${r.value.slice(3)}` : what === 'password' ? 'Skopiowano hasło · zniknie za 30 s' : 'Skopiowano login');
}

view.addEventListener('click', (e) => {
  if (e.target.closest('.addhere')) { showAdd(); return; }
  if (e.target.closest('.addotp')) { addOtp(); return; }
  const row = e.target.closest('.row');
  if (!row || !row.dataset.id) return;
  const c = e.target.closest('[data-copy]');
  if (c) { copy(row.dataset.id, c.dataset.copy); return; }
  act(row.dataset.id, !!e.target.closest('[data-fill]') || (renderList.here && renderList.here.has(row.dataset.id) && !e.target.closest('.go')));
});

document.addEventListener('keydown', (e) => {
  const order = renderList.order || [];
  if (!order.length) return;
  if (e.key === 'ArrowDown') { sel = Math.min(order.length - 1, sel + 1); renderList(); e.preventDefault(); }
  if (e.key === 'ArrowUp') { sel = Math.max(0, sel - 1); renderList(); e.preventDefault(); }
  if (e.key === 'Enter') { const it = order[sel]; if (it) act(it.id, renderList.here.has(it.id)); }
});

// --- dodawanie hasła z okienka ---

function titleFromHost(host) {
  const d = (host || '').replace(/^www\./, '').split('.');
  const name = d.length >= 2 ? d[d.length - 2] : d[0] || '';
  return name ? name[0].toUpperCase() + name.slice(1) : '';
}

async function showAdd() {
  // Na stronie logowania (/login, /logowanie…) zapisujemy cały adres - Ostoja otworzy potem od razu formularz.
  const tabUrl = /^https?:/.test(data.tabUrl || '') ? new URL(data.tabUrl) : null;
  const url = !tabUrl ? '' : /\/(login|log-in|signin|sign-in|sign_in|logowanie|zaloguj|logon|session\/new|users\/sign_in)(\/|$|\.)/i.test(tabUrl.pathname) ? tabUrl.origin + tabUrl.pathname : tabUrl.origin;
  view.innerHTML = `<div class="form">
    <div class="label">Nowe hasło</div>
    <div class="card pad">
      <label>Nazwa<input id="f-title" value="${esc(titleFromHost(data.host))}" placeholder="np. Allegro"></label>
      <label>Strona<input id="f-url" value="${esc(url)}" placeholder="https://…"></label>
      <label>Login lub e-mail<input id="f-user" autocomplete="off"></label>
      <label>Hasło<span class="pw"><input id="f-pw" type="password" autocomplete="new-password"><button class="icon" id="f-eye" title="Pokaż">${ICON.eye}</button><button class="icon acc" id="f-gen" title="Wygeneruj silne hasło">${ICON.regen}</button></span></label>
    </div>
    <div class="buttons"><button class="btn ghost" id="f-cancel">Anuluj</button><button class="btn" id="f-save">Zapisz</button></div>
  </div>`;
  $('#f-user').focus();
  $('#f-eye').addEventListener('click', () => { const i = $('#f-pw'); i.type = i.type === 'password' ? 'text' : 'password'; });
  $('#f-gen').addEventListener('click', async () => { const r = await send({ type: 'generate', length: 20 }); $('#f-pw').value = r.value; $('#f-pw').type = 'text'; });
  $('#f-cancel').addEventListener('click', () => showList());
  $('#f-save').addEventListener('click', async () => {
    const entry = { title: $('#f-title').value.trim(), url: $('#f-url').value.trim(), username: $('#f-user').value.trim(), password: $('#f-pw').value };
    if (!entry.title && !entry.url) { toast('Podaj nazwę albo stronę'); return; }
    if (!entry.password) { toast('Wpisz albo wygeneruj hasło'); return; }
    if (entry.url && !/^https?:\/\//.test(entry.url)) entry.url = 'https://' + entry.url;
    const r = await send({ type: 'add', ...entry });
    if (!r || r.error) { toast(r ? r.error : 'Błąd'); return; }
    toast('Zapisano · ' + r.title);
    data = await send({ type: 'list', force: true });
    setTimeout(showList, 700);
  });
}

$('#add').addEventListener('click', () => { if (state && state.status === 'ready') showAdd(); });
$('#menu').addEventListener('click', () => { $('#menuBox').hidden = !$('#menuBox').hidden; });
$('#refresh').addEventListener('click', () => { $('#menuBox').hidden = true; init(true); });
$('#unpair').addEventListener('click', async () => { $('#menuBox').hidden = true; await send({ type: 'unpair' }); init(true); });

async function init(force) {
  state = await send({ type: 'status', force: !!force });
  if (state.appState) {
    const a = ACCENTS[state.appState.accent] || ACCENTS[0];
    document.documentElement.style.setProperty('--accent', a);
    document.documentElement.style.setProperty('--tint', a + (matchMedia('(prefers-color-scheme: dark)').matches ? '2E' : '1A'));
    icons = state.appState.icons !== false;
  }
  $('#menu').hidden = !(state.status === 'ready' || state.status === 'locked');
  $('#add').hidden = state.status !== 'ready';
  if (state.pairing && state.pairing.state === 'waiting' && state.status === 'unpaired') {
    view.innerHTML = '';
    return showUnpaired();
  }
  switch (state.status) {
    case 'unpaired': return showUnpaired();
    case 'offline': case 'starting': return showOffline();
    case 'locked': return showLocked();
  }
  data = await send({ type: 'list' });
  showList();
}

init(false);
