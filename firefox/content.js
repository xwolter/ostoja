/*
 * Ostoja - silnik logowania na stronie.
 *
 * Gdy tło ma dla tej karty zadanie logowania, skrypt:
 *   1. znajduje formularz (pole hasła i najbardziej prawdopodobne pole loginu, także w otwartych shadow DOM),
 *   2. przy logowaniu dwuetapowym (Google, Microsoft…) wpisuje login, klika „Dalej” i czeka na pole hasła,
 *   3. wpisuje hasło tak, by zauważyły to React/Vue/Angular, i wysyła formularz,
 *   4. jeśli pojawi się pole kodu 2FA (także 6 osobnych pól), pobiera świeży kod z aplikacji i go wpisuje.
 * Zadanie przeżywa przeładowanie strony: każdy etap jest zapisywany w tle, a nowa strona go kontynuuje.
 * Hasło jest wpisywane wyłącznie na domenie wpisu - tło sprawdza adres karty przed jego wydaniem.
 */
(() => {
  'use strict';
  if (window.top !== window || window.__ostoja) return;
  window.__ostoja = true;

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const attrs = (el) => [el.name, el.id, el.placeholder, el.getAttribute('aria-label'), el.autocomplete, el.className]
    .filter(Boolean).join(' ').toLowerCase();

  function visible(el) {
    if (!el || !el.isConnected || el.disabled || el.readOnly) return false;
    const r = el.getBoundingClientRect();
    if (r.width < 4 || r.height < 4) return false;
    const s = getComputedStyle(el);
    return s.visibility !== 'hidden' && s.display !== 'none' && parseFloat(s.opacity || '1') > 0.05;
  }

  /** Wszystkie widoczne pola, także w otwartych shadow DOM (np. strony na Web Components). */
  function inputs() {
    const out = [];
    const walk = (root) => {
      root.querySelectorAll('input').forEach((i) => out.push(i));
      root.querySelectorAll('*').forEach((el) => { if (el.shadowRoot) walk(el.shadowRoot); });
    };
    walk(document);
    return out.filter(visible);
  }

  const TEXTY = ['text', 'email', 'tel', ''];

  function isOtpLike(i) {
    if (i.autocomplete === 'one-time-code') return true;
    const a = attrs(i);
    if (/phone|telefon|search|szuk|user|login|mail|captcha/.test(a)) return false;
    if (/(otp|totp|2fa|mfa|one.?time|verif|security.?code|auth.?code|kod|token|pin\b|code)/.test(a)) return true;
    return (i.inputMode === 'numeric' || i.type === 'tel' || i.pattern === '[0-9]*') && i.maxLength >= 6 && i.maxLength <= 8;
  }

  function passwordField() {
    return inputs().find((i) => i.type === 'password' && !/(confirm|repeat|new|powt|nowe)/.test(attrs(i)));
  }

  /** Pole loginu: punktacja po autocomplete, nazwie, typie i położeniu względem pola hasła. */
  function userField(pw) {
    const all = inputs();
    let best = null;
    let bestScore = 0;
    for (const i of all) {
      if (!TEXTY.includes(i.type) || isOtpLike(i)) continue;
      const a = attrs(i);
      if (/(search|szuk|query|newsletter|coupon|kupon)/.test(a)) continue;
      if (!pw && signupForm(i)) continue;
      let s = 1;
      if (/username|email/.test(i.autocomplete)) s += 6;
      if (/(user|login|email|mail|konto|account|identifier|nick|uzytkownik|użytkownik)/.test(a)) s += 4;
      if (i.type === 'email') s += 2;
      if (pw) {
        if (pw.form && i.form === pw.form) s += 3;
        if (i.compareDocumentPosition(pw) & Node.DOCUMENT_POSITION_FOLLOWING) s += 1;
      }
      if (s > bestScore) { best = i; bestScore = s; }
    }
    return best;
  }

  const SIGNUP = /(sign ?up|zarejestr|załóż|utwórz|create|register|join|subscribe|zapisz się|newsletter|get started|start free|try )/i;

  /** Pole e-mail na stronie głównej to zwykle rejestracja („Sign up for GitHub”), a nie logowanie. */
  function signupForm(i) {
    const scope = i.form || i.closest('form') || (i.parentElement && i.parentElement.parentElement);
    if (!scope) return false;
    const btns = [...scope.querySelectorAll('button, input[type=submit]')].map(label);
    return btns.some((t) => SIGNUP.test(t)) && !btns.some((t) => /(zaloguj|log ?in|sign ?in|dalej|next|continue)/i.test(t) && !SIGNUP.test(t));
  }

  function otpFields() {
    const all = inputs().filter((i) => i.type !== 'password' && i.type !== 'hidden' && i.type !== 'checkbox');
    const single = all.find(isOtpLike);
    if (single && single.maxLength !== 1) return [single];
    const ones = all.filter((i) => i.maxLength === 1 && TEXTY.concat(['number', 'tel']).includes(i.type));
    if (ones.length >= 6) return ones.slice(0, 8);
    return single ? [single] : null;
  }

  /** Wpisanie wartości tak, jak robi to człowiek - frameworki widzą zdarzenia input/change. */
  function setNative(el, v) {
    const desc = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
    desc.set.call(el, v);
  }

  async function type(el, value) {
    el.focus();
    el.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    setNative(el, '');
    el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'deleteContentBackward' }));
    setNative(el, value);
    el.dispatchEvent(new InputEvent('input', { bubbles: true, data: value, inputType: 'insertText' }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: value.slice(-1) || 'a' }));
    await sleep(80);
  }

  const SUBMIT_TEXT = /^\s*(zaloguj( się)?|log ?in|sign ?in|next|dalej|continue|kontynuuj|submit|verify|zweryfikuj|potwierdź|weiter|anmelden|enter|ok)\b/i;
  const NOT_SUBMIT = /(google|facebook|apple|microsoft|github|sso|passkey|klucz dostępu|create|utwórz|zarejestr|register|sign ?up|forgot|zapomn|reset|anuluj|cancel|back|wstecz)/i;

  function label(b) {
    return (b.innerText || b.value || b.getAttribute('aria-label') || '').trim();
  }

  function findButton(scope) {
    const pick = (root) => {
      const cands = [...root.querySelectorAll('button, input[type=submit], input[type=button], [role=button]')].filter(visible);
      const typed = cands.find((b) => (b.type === 'submit' || b.getAttribute('type') === 'submit') && !NOT_SUBMIT.test(label(b)));
      return typed || cands.find((b) => SUBMIT_TEXT.test(label(b)) && !NOT_SUBMIT.test(label(b)));
    };
    return (scope && pick(scope)) || pick(document);
  }

  function submit(el) {
    const form = el.form || el.closest('form');
    const btn = findButton(form);
    if (btn) { btn.click(); return; }
    if (form) {
      try { form.requestSubmit(); } catch (e) { form.submit(); }
      return;
    }
    for (const t of ['keydown', 'keypress', 'keyup']) {
      el.dispatchEvent(new KeyboardEvent(t, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
    }
  }

  /** Czeka, aż funkcja zwróci coś prawdziwego (DOM się zmienia, strona doładowuje formularz). */
  function waitFor(fn, timeout) {
    return new Promise((resolve) => {
      const first = fn();
      if (first) return resolve(first);
      let done = false;
      const finish = (v) => { if (done) return; done = true; obs.disconnect(); clearInterval(iv); clearTimeout(to); resolve(v); };
      const check = () => { const v = fn(); if (v) finish(v); };
      const obs = new MutationObserver(check);
      obs.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class', 'hidden', 'type'] });
      const iv = setInterval(check, 250);
      const to = setTimeout(() => finish(null), timeout);
    });
  }

  // --- pigułka postępu na stronie (shadow DOM, żeby styl strony jej nie ruszał) ---

  const pill = (() => {
    let host = null;
    let box = null;
    let hideTimer = null;
    const css = `
      :host { all: initial; }
      .p { position: fixed; top: 16px; right: 16px; z-index: 2147483647; display: flex; align-items: center; gap: 12px;
           padding: 10px 14px 10px 10px; border-radius: 22px; min-width: 240px; max-width: 380px;
           font: 13.5px/1.35 "Segoe UI", Roboto, system-ui, sans-serif; color: #16181d; background: rgba(255,255,255,.97);
           border: 1px solid rgba(0,0,0,.07); box-shadow: 0 18px 40px -18px rgba(0,0,0,.35);
           transform: translateY(-24px) scale(.96); opacity: 0;
           transition: transform 420ms cubic-bezier(.34,1.45,.64,1), opacity 220ms ease; }
      .p.on { transform: none; opacity: 1; }
      @media (prefers-color-scheme: dark) { .p { color: #f2f3f5; background: rgba(30,32,38,.97); border-color: rgba(255,255,255,.09); } }
      .ic { position: relative; width: 34px; height: 34px; flex: none; border-radius: 50%; background: #3E91FF; display: grid; place-items: center; }
      .ic svg { width: 17px; height: 17px; }
      .ring { position: absolute; inset: -4px; border-radius: 50%; border: 2.5px solid rgba(62,145,255,.25); border-top-color: #3E91FF; animation: spin .9s linear infinite; }
      .ok .ic { background: #2E9E74; } .ok .ring, .err .ring { display: none; }
      .err .ic { background: #E5484D; }
      .ok .ic, .err .ic { animation: pop 420ms cubic-bezier(.34,1.7,.64,1); }
      b { display: block; font-weight: 600; font-size: 13px; }
      span { opacity: .75; }
      .x { margin-left: auto; width: 26px; height: 26px; border-radius: 50%; border: 0; background: transparent; color: inherit; opacity: .5; cursor: pointer; font-size: 16px; }
      .x:hover { opacity: 1; background: rgba(127,127,127,.15); }
      @keyframes spin { to { transform: rotate(360deg); } }
      @keyframes pop { from { transform: scale(.4); } to { transform: scale(1); } }`;
    const lock = '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round"><rect x="5" y="10.5" width="14" height="10" rx="2.6"/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"/></svg>';
    const check = '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';
    const cross = '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.8" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>';

    function ensure() {
      if (host) return;
      host = document.createElement('ostoja-pill');
      const root = host.attachShadow({ mode: 'closed' });
      root.innerHTML = `<style>${css}</style><div class="p"><div class="ic"><div class="ring"></div>${lock}</div><div><b>Ostoja</b><span></span></div><button class="x" title="Przerwij">×</button></div>`;
      box = root.querySelector('.p');
      root.querySelector('.x').addEventListener('click', () => { cancelled = true; hide(); browser.runtime.sendMessage({ type: 'jobDone', ok: false }); });
      document.documentElement.appendChild(host);
      requestAnimationFrame(() => requestAnimationFrame(() => box.classList.add('on')));
    }

    function set(text, state) {
      ensure();
      clearTimeout(hideTimer);
      box.classList.remove('ok', 'err');
      if (state) {
        box.classList.add(state);
        box.querySelector('.ic').innerHTML = state === 'ok' ? check : cross;
        hideTimer = setTimeout(hide, state === 'ok' ? 2600 : 6000);
      }
      box.querySelector('span').textContent = text;
    }

    function hide() {
      if (!box) return;
      box.classList.remove('on');
      const h = host;
      host = null;
      setTimeout(() => h && h.remove(), 400);
    }

    return { set };
  })();

  // --- zapisywanie: po zalogowaniu się ręcznie Ostoja pyta, czy zapisać (albo zaktualizować) hasło ---

  let lastCapture = 0;

  function capture() {
    if (running) return;
    const pw = passwordField();
    if (!pw) {
      // Pierwszy krok logowania dwuetapowego - zapamiętujemy login na następną stronę.
      const u = userField(null);
      if (u && u.value) browser.runtime.sendMessage({ type: 'userSeen', username: u.value });
      return;
    }
    if (!pw.value || Date.now() - lastCapture < 1500) return;
    lastCapture = Date.now();
    const u = userField(pw);
    browser.runtime.sendMessage({ type: 'captured', username: u ? u.value : '', password: pw.value });
  }

  document.addEventListener('submit', capture, true);
  document.addEventListener('click', (e) => {
    const b = e.target && e.target.closest ? e.target.closest('button, input[type=submit], [role=button]') : null;
    if (b && (b.type === 'submit' || SUBMIT_TEXT.test(label(b)))) capture();
  }, true);
  document.addEventListener('keydown', (e) => { if (e.key === 'Enter' && e.target && e.target.tagName === 'INPUT') capture(); }, true);

  const saveBar = (() => {
    let host = null;
    const css = `
      :host { all: initial; }
      .c { position: fixed; top: 16px; right: 16px; z-index: 2147483647; width: 340px; padding: 16px; border-radius: 22px;
           font: 13.5px/1.4 "Segoe UI", Roboto, system-ui, sans-serif; color: #16181d; background: rgba(255,255,255,.98);
           border: 1px solid rgba(0,0,0,.07); box-shadow: 0 22px 50px -20px rgba(0,0,0,.4);
           transform: translateY(-24px) scale(.96); opacity: 0; transition: transform 420ms cubic-bezier(.34,1.45,.64,1), opacity 220ms; }
      .c.on { transform: none; opacity: 1; }
      @media (prefers-color-scheme: dark) { .c { color: #f2f3f5; background: rgba(30,32,38,.98); border-color: rgba(255,255,255,.09); } .ghost { background: rgba(255,255,255,.08) !important; } }
      .h { display: flex; align-items: center; gap: 10px; }
      .ic { width: 34px; height: 34px; border-radius: 50%; background: #3E91FF; display: grid; place-items: center; flex: none; }
      .ic svg { width: 17px; height: 17px; }
      b { display: block; font-size: 15px; font-weight: 600; }
      .s { opacity: .7; font-size: 12.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 260px; }
      .r { display: flex; gap: 8px; margin-top: 14px; }
      button { font: 600 13.5px "Segoe UI", system-ui, sans-serif; height: 38px; border-radius: 19px; border: 0; cursor: pointer; flex: 1; transition: transform 200ms cubic-bezier(.34,1.5,.64,1); }
      button:active { transform: scale(.95); }
      .save { background: #3E91FF; color: #fff; }
      .ghost { background: rgba(0,0,0,.06); color: inherit; }
      .never { display: block; margin: 10px auto 0; background: none; height: auto; font-weight: 500; font-size: 12px; opacity: .6; flex: none; color: inherit; }
      .never:hover { opacity: 1; }`;
    const lock = '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round"><rect x="5" y="10.5" width="14" height="10" rx="2.6"/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"/></svg>';

    function close() {
      if (!host) return;
      const h = host;
      host = null;
      const box = h._box;
      box.classList.remove('on');
      setTimeout(() => h.remove(), 400);
    }

    function show(p) {
      if (host) return;
      host = document.createElement('ostoja-save');
      const root = host.attachShadow({ mode: 'closed' });
      const title = p.state === 'changed' ? `Zaktualizować hasło do ${p.title}?` : 'Zapisać hasło w Ostoi?';
      const who = (p.username || 'bez loginu') + ' · ' + p.host;
      root.innerHTML = `<style>${css}</style><div class="c"><div class="h"><div class="ic">${lock}</div><div><b></b><div class="s"></div></div></div>
        <div class="r"><button class="ghost" data-d="later">Nie teraz</button><button class="save" data-d="save">${p.state === 'changed' ? 'Zaktualizuj' : 'Zapisz'}</button></div>
        <button class="never" data-d="never">Nigdy dla ${p.host}</button></div>`;
      root.querySelector('b').textContent = title;
      root.querySelector('.s').textContent = who;
      const box = root.querySelector('.c');
      host._box = box;
      root.addEventListener('click', async (e) => {
        const d = e.isTrusted && e.target.closest('[data-d]');
        if (!d) return;
        const r = await browser.runtime.sendMessage({ type: 'saveDecision', decision: d.dataset.d });
        close();
        if (d.dataset.d === 'save') pill.set(r && r.ok ? `Zapisano w Ostoi · ${r.title} trafi też na telefon` : (r && r.error) || 'Nie udało się zapisać', r && r.ok ? 'ok' : 'err');
      });
      document.documentElement.appendChild(host);
      requestAnimationFrame(() => requestAnimationFrame(() => box.classList.add('on')));
      setTimeout(close, 30000);
    }

    /** To samo okienko z jednym pytaniem i przyciskiem „tak” (np. „Dodać kod 2FA do Ostoi?”). */
    function offer(title, sub, yes, onYes) {
      if (host) return;
      host = document.createElement('ostoja-save');
      const root = host.attachShadow({ mode: 'closed' });
      root.innerHTML = `<style>${css}</style><div class="c"><div class="h"><div class="ic">${lock}</div><div><b></b><div class="s"></div></div></div>
        <div class="r"><button class="ghost" data-d="no">Nie teraz</button><button class="save" data-d="yes"></button></div></div>`;
      root.querySelector('b').textContent = title;
      root.querySelector('.s').textContent = sub;
      root.querySelector('.save').textContent = yes;
      const box = root.querySelector('.c');
      host._box = box;
      root.addEventListener('click', (e) => {
        const d = e.isTrusted && e.target.closest('[data-d]');
        if (!d) return;
        close();
        if (d.dataset.d === 'yes') onYes();
      });
      document.documentElement.appendChild(host);
      requestAnimationFrame(() => requestAnimationFrame(() => box.classList.add('on')));
      setTimeout(close, 60000);
    }

    return { show, offer };
  })();

  // --- strona włączania 2FA z kodem QR (GitHub, Google, Microsoft…): Ostoja proponuje dodanie kodu ---

  const SETUP_TEXT = /(two-factor|2fa|authenticator|authentication app|scan (the|this) (qr|code)|qr code|uwierzytelni|dwuetapow|dwuskładnik|weryfikacj\S* dwu|zeskanuj|kod qr)/i;

  function qrCandidate() {
    return [...document.querySelectorAll('img, svg, canvas, table')].find((el) => {
      const r = el.getBoundingClientRect();
      if (r.width < 80 || r.width > 600 || Math.abs(r.width - r.height) > r.width * 0.15 || !visible(el)) return false;
      // Ikony i logotypy SVG są małe albo mają mało elementów; kod QR ma setki modułów.
      if (el.tagName === 'svg' || el.tagName === 'SVG') return el.querySelectorAll('rect, path').length > 0 && (el.innerHTML.length > 2000);
      if (el.tagName === 'TABLE') return el.querySelectorAll('td').length > 200;
      return el.tagName === 'CANVAS' || r.width >= 100 || /qr|otp|two.?factor|2fa|totp/i.test([el.alt, el.className, el.id].join(' '));
    });
  }

  let otpOffered = false;
  function lookForOtpSetup() {
    if (otpOffered || running || !SETUP_TEXT.test(document.body ? document.body.innerText.slice(0, 20000) : '')) return;
    if (!qrCandidate()) return;
    otpOffered = true;
    saveBar.offer('Dodać ten kod 2FA do Ostoi?', `Kody dla ${location.hostname} będą w Ostoi i na telefonie`, 'Dodaj kod', async () => {
      pill.set('Odczytuję kod QR…');
      await sleep(450); // okienko Ostoi znika, zanim zrobimy zrzut karty
      const r = await browser.runtime.sendMessage({ type: 'addOtpTab' }).catch((e) => ({ error: e.message }));
      pill.set(r && r.ok ? `Dodano kod 2FA · ${r.title}. Wpisz na stronie kod z Ostoi, żeby potwierdzić` : (r && r.error) || 'Nie udało się dodać kodu', r && r.ok ? 'ok' : 'err');
    });
  }

  let setupTimer = null;
  new MutationObserver(() => { clearTimeout(setupTimer); setupTimer = setTimeout(lookForOtpSetup, 700); })
    .observe(document.documentElement, { childList: true, subtree: true });
  setTimeout(lookForOtpSetup, 1200);

  async function offerSave() {
    let p;
    try { p = await browser.runtime.sendMessage({ type: 'pendingSave?' }); } catch (e) { return; }
    if (p) saveBar.show(p);
  }

  // --- przebieg logowania ---

  let running = false;
  let autoBusy = false;
  let cancelled = false;

  const phase = (p) => browser.runtime.sendMessage({ type: 'jobPhase', phase: p });

  function done(ok, msg) {
    pill.set(msg, ok ? 'ok' : 'err');
    browser.runtime.sendMessage({ type: 'jobDone', ok });
  }

  // --- przełączenie na kod z aplikacji, gdy strona proponuje najpierw passkey / SMS (np. GitHub, Google, Microsoft) ---

  const APP_OPTION = /(authenticator|authentication app|aplikacj\S* (uwierzytel|authenticator|do weryfikacji)|kod z aplikacji|code from (your |an )?(authenticator |authentication )?app|use (your |an )?(authenticator|authentication) app|google authenticator|totp)/i;
  const MORE_OPTIONS = /^\s*(more options|więcej opcji|other options|inne opcje|try another way|spróbuj (w )?inn\S* (sposób|metod)|use (a |another |a different )?(method|option|way)|choose another|wybierz inn|sign in another way|zaloguj się w inny sposób|inna metoda)/i;
  const NOT_APP = /(passkey|security key|klucz (dostępu|bezpiecze)|sms|text message|wiadomoś|e-?mail|recovery|odzysk|kod zapasow|backup code|call|połącz)/i;

  function clickables() {
    return [...document.querySelectorAll('a, button, summary, [role=button], [role=menuitem], [role=menuitemradio], [role=option], [role=link], li[tabindex]')].filter(visible);
  }

  function findOption(re) {
    // Najkrótszy pasujący element - sam przycisk, a nie cały kontener z opisem.
    return clickables()
      .filter((el) => {
        const t = label(el);
        if (!re.test(t)) return false;
        // Opcja „aplikacja” nie może być passkeyem ani SMS-em (chyba że wprost mówi o authenticatorze).
        return re !== APP_OPTION || !NOT_APP.test(t) || /authenticator/i.test(t);
      })
      .sort((a, b) => label(a).length - label(b).length)[0];
  }

  // --- strona główna bez formularza: przejście do logowania (/login, /logowanie, okienko „Zaloguj się”) ---

  const LOGIN_TEXT = /^\s*(zaloguj( się)?|logowanie|log ?in|log ?on|sign ?in|login|moje konto|twoje konto|my account|konto|account|anmelden|einloggen|connexion|se connecter|iniciar sesión|accedi)\s*$/i;
  const LOGIN_PATH = /\/(login|log-in|log_in|logon|signin|sign-in|sign_in|logowanie|zaloguj|session\/new|users\/sign_in|auth\/login|account\/login)(\/|$|\.)/i;

  /** Link albo przycisk do logowania; link do tej samej strony się nie liczy (żeby nie kręcić się w kółko). */
  function loginLink() {
    const here = location.origin + location.pathname;
    let best = null;
    let bestScore = 0;
    for (const el of clickables()) {
      const t = label(el);
      if (t.length > 30 || /(google|facebook|apple|microsoft|sso|passkey|sign ?up|zarejestr|załóż|utwórz|create|register)/i.test(t)) continue;
      const raw = el.tagName === 'A' ? el.getAttribute('href') || '' : '';
      let url = null;
      if (raw && !raw.startsWith('#') && !/^javascript:/i.test(raw)) {
        try { url = new URL(raw, location.href); } catch (e) { continue; }
        if (!/^https?:$/.test(url.protocol) || url.origin + url.pathname === here) continue;
      }
      let sc = 0;
      if (LOGIN_TEXT.test(t)) sc += 4;
      if (url && LOGIN_PATH.test(url.pathname)) sc += 3;
      if (!sc) continue;
      if (url) sc += 1;
      if (sc > bestScore) { best = { el, url }; bestScore = sc; }
    }
    return best;
  }

  async function switchToAuthenticator() {
    let el = findOption(APP_OPTION);
    if (!el) {
      const more = findOption(MORE_OPTIONS);
      if (!more) return false;
      pill.set('Szukam opcji „kod z aplikacji”…');
      more.click();
      el = await waitFor(() => findOption(APP_OPTION), 4000);
    }
    if (!el) return false;
    pill.set('Przełączam na kod z aplikacji…');
    el.click();
    return true;
  }

  async function otpPhase(job) {
    pill.set(job.hasOtp ? 'Czekam na pole kodu 2FA…' : 'Sprawdzam logowanie…');
    let fields = await waitFor(otpFields, job.hasOtp ? 4000 : 5000);
    if (!fields && job.hasOtp && !cancelled) {
      // Licznik w sessionStorage przeżywa przeładowanie strony - nie klikamy w kółko.
      const n = Number(sessionStorage.getItem('ostoja-2fa-switch') || 0);
      if (n < 3) {
        sessionStorage.setItem('ostoja-2fa-switch', String(n + 1));
        if (await switchToAuthenticator()) fields = await waitFor(otpFields, 12000);
      } else {
        fields = await waitFor(otpFields, 8000);
      }
    }
    if (cancelled) return;
    if (!fields) { done(true, `Zalogowano do ${job.title}`); return; }
    // Nawet gdy wpis nie był połączony z kodem, aplikacja może go znaleźć po nazwie serwisu.
    pill.set('Pobieram kod 2FA z Ostoi…');
    const r = await askCode('jobOtp');
    if (cancelled) return;
    if (!r || !r.ok) { done(false, job.hasOtp ? 'Nie udało się pobrać kodu 2FA' : `Strona prosi o kod 2FA, a w Ostoi nie ma kodu dla ${job.title}`); return; }
    await phase('done');
    await typeOtp(fields, r.code);
    done(true, `Zalogowano do ${job.title} z kodem 2FA`);
  }

  /** Kod z Ostoi; gdy bieżący był już wpisany na tej stronie, czekamy na następny (najwyżej jeden okres). */
  async function askCode(kind) {
    for (let i = 0; i < 3; i++) {
      let r;
      try { r = await browser.runtime.sendMessage({ type: kind }); } catch (e) { return null; }
      if (!r || !r.wait) return r;
      for (let left = r.wait; left > 0 && !cancelled; left--) {
        pill.set(`Ten kod 2FA był już użyty - czekam na nowy (${left} s)…`);
        await sleep(1000);
      }
      if (cancelled) return null;
    }
    return null;
  }

  async function typeOtp(fields, code) {
    pill.set('Wpisuję kod 2FA…');
    // GitHub i wiele innych stron wysyła formularz sam po ostatniej cyfrze. Gdybyśmy kliknęli jeszcze „Verify”,
    // ten sam kod poszedłby drugi raz i strona odpowiedziałaby „already been used”.
    let sent = false;
    const mark = () => { sent = true; };
    document.addEventListener('submit', mark, true);
    window.addEventListener('beforeunload', mark);
    window.addEventListener('pagehide', mark);
    try {
      if (fields.length === 1) await type(fields[0], code);
      else for (let k = 0; k < fields.length && k < code.length; k++) await type(fields[k], code[k]);
      const last = fields[fields.length - 1];
      for (let t = 0; t < 15 && !sent; t++) await sleep(100);
      if (!sent && last.isConnected && visible(last)) submit(last);
    } finally {
      document.removeEventListener('submit', mark, true);
      window.removeEventListener('beforeunload', mark);
      window.removeEventListener('pagehide', mark);
    }
  }

  /**
   * Ekran kodu 2FA bez zadania logowania (hasło wpisane ręcznie albo przez Firefoksa, zadanie wygasło):
   * jeśli Ostoja ma kod dla tej strony, wpisuje go sama. Najwyżej dwa razy na kartę i adres -
   * przy odrzuconym kodzie strona przeładowuje się i nie chcemy kręcić się w kółko.
   */
  async function autoOtp() {
    const fields = await waitFor(() => !passwordField() && otpFields(), 6000);
    if (!fields || running || autoBusy) return;
    const key = 'ostoja-otp-auto:' + location.pathname;
    const n = Number(sessionStorage.getItem(key) || 0);
    if (n >= 2) return;
    autoBusy = true;
    try {
      const r = await askCode('otpHere');
      if (r && r.error === 'locked') { pill.set('Sejf Ostoi jest zablokowany - kliknij ikonę Ostoi i potwierdź na telefonie, a wpiszę kod 2FA', 'err'); return; }
      if (!r || !r.code || running) return;
      sessionStorage.setItem(key, String(n + 1));
      await typeOtp(fields, r.code);
      pill.set(`Wpisano kod 2FA dla ${r.title}`, 'ok');
    } finally {
      autoBusy = false;
    }
  }

  async function run(job) {
    pill.set(`Loguję do ${job.title}…`);
    if (job.phase === 'otp' || job.phase === 'done') return otpPhase(job);

    let pw = null;
    if (job.phase === 'password') {
      pw = await waitFor(passwordField, 15000);
    } else {
      let user = null;
      for (;;) {
        // Najpierw chwila na formularz; dopiero potem szukamy linku „Zaloguj się” (strona główna bez logowania).
        if (!(await waitFor(() => passwordField() || userField(null), 2500))) await waitFor(() => passwordField() || userField(null) || loginLink(), 8000);
        if (cancelled) return;
        pw = passwordField();
        user = userField(pw);
        if (pw || user) break;
        const link = loginLink();
        if (!link || !(await browser.runtime.sendMessage({ type: 'jobHop' }).catch(() => false))) {
          done(false, 'Nie znalazłem formularza logowania - możliwe, że już jesteś zalogowany');
          return;
        }
        pill.set('Przechodzę do strony logowania…');
        // Link na nowej stronie: tło przekaże zadanie nowej stronie. Przycisk albo „#”: okienko na tej samej stronie.
        if (link.url) location.assign(link.url.href);
        else link.el.click();
        await sleep(1500);
      }
      browser.runtime.sendMessage({ type: 'jobLoginUrl', url: location.origin + location.pathname }).catch(() => {});
      if (user && job.username) {
        pill.set('Wpisuję login…');
        await type(user, job.username);
      }
      if (!pw) {
        // Logowanie w dwóch krokach: login → „Dalej” → hasło (czasem na nowej stronie).
        if (!user) { done(false, 'Nie znalazłem pola loginu'); return; }
        await phase('password');
        pill.set('Dalej…');
        await sleep(250);
        submit(user);
        pw = await waitFor(passwordField, 15000);
      }
    }
    if (cancelled) return;
    if (!pw) { done(false, 'Nie pojawiło się pole hasła'); return; }
    pill.set('Wpisuję hasło…');
    await sleep(200);
    await type(pw, job.password);
    await phase('otp');
    await sleep(250);
    submit(pw);
    await sleep(600);
    return otpPhase(job);
  }

  async function start() {
    if (running) return;
    let job;
    try { job = await browser.runtime.sendMessage({ type: 'job?' }); } catch (e) { return; }
    if (!job) { autoOtp(); return; }
    if (job.insecure) {
      pill.set(`Nie wpiszę hasła do ${job.title}: ta strona nie jest szyfrowana (http://). Sprawdź adres.`, 'err');
      browser.runtime.sendMessage({ type: 'jobDone', ok: false });
      return;
    }
    if (job.mismatch) {
      pill.set(`Nie wpiszę hasła: to ${location.hostname}, a wpis „${job.title}” jest dla ${job.domain}`, 'err');
      browser.runtime.sendMessage({ type: 'jobDone', ok: false });
      return;
    }
    running = true;
    cancelled = false;
    try {
      await run(job);
    } catch (e) {
      done(false, 'Nie udało się zalogować: ' + e.message);
    } finally {
      running = false;
    }
  }

  browser.runtime.onMessage.addListener((m) => {
    if (m.type === 'job') start();
    if (m.type === 'offerSave') offerSave();
  });
  start();
  setTimeout(offerSave, 600);
})();
