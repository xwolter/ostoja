import Image from 'next/image';
import { FirefoxDemo } from '@/components/FirefoxDemo';
import { Ic } from '@/components/Icons';
import { Reveal } from '@/components/Reveal';
import { TotpDemo } from '@/components/TotpDemo';
import { download, site } from '@/lib/site';
import logo from '@/assets/logo.svg';
import vault from '@/assets/shots/desktop-vault-dark.webp';
import health from '@/assets/shots/desktop-health.webp';
import codes from '@/assets/shots/desktop-codes.webp';
import phoneVault from '@/assets/shots/phone-vault.webp';
import phoneCodes from '@/assets/shots/phone-codes.webp';
import phoneItem from '@/assets/shots/phone-item.webp';

const FEATURES = [
  { icon: Ic.Lock, title: 'Sejf haseł', text: 'Loginy, karty płatnicze, notatki i sieci Wi‑Fi. Generator silnych haseł i historia zmian przy każdym wpisie.' },
  { icon: Ic.Timer, title: 'Kody 2FA', text: 'TOTP i HOTP, 6-8 cyfr, SHA‑1/256/512. Kod QR zeskanujesz aparatem, z ekranu komputera albo prosto ze strony w Firefoksie.' },
  { icon: Ic.Firefox, title: 'Logowanie jednym kliknięciem', text: 'Rozszerzenie otwiera stronę, samo znajduje formularz, wpisuje login, hasło i kod 2FA. Także przy logowaniu w dwóch krokach.' },
  { icon: Ic.Fingerprint, title: 'Potwierdzenie na telefonie', text: 'Komputer odblokowujesz odciskiem palca na telefonie. Wybierasz tę samą liczbę, którą pokazuje ekran, więc nie zatwierdzisz niczego na ślepo.' },
  { icon: Ic.Wifi, title: 'Synchronizacja bez chmury', text: 'Telefon i komputer wymieniają zmiany bezpośrednio w domowej sieci. Dane nie trafiają na żaden serwer.' },
  { icon: Ic.Heart, title: 'Kondycja sejfu', text: 'Słabe, powtórzone i stare hasła w jednym miejscu. Opcjonalnie sprawdzanie wycieków przez Have I Been Pwned (k‑anonimowość).' },
];

const SECURITY = [
  { icon: Ic.Key, title: 'AES‑256‑GCM na każdym zapytaniu', text: 'Klucz kanału powstaje z 32‑bajtowego sekretu z kodu QR (HKDF‑SHA256). Sam sekret nigdy nie idzie przez sieć.', tag: 'HKDF · AES-GCM' },
  { icon: Ic.ShieldCheck, title: 'Parowanie przez ECDH', text: 'Rozszerzenie i aplikacja wymieniają klucze P‑256 i pokazują ten sam 6‑cyfrowy kod. Połączenie zatwierdzasz ręcznie.', tag: 'ECDH P-256' },
  { icon: Ic.CloudOff, title: 'Bez konta i bez serwera', text: 'Każde urządzenie trzyma pełny sejf. Przy spotkaniu stany są scalane wpis po wpisie, a usunięcia przenoszą „nagrobki”.', tag: 'local-first' },
  { icon: Ic.Lock, title: 'Hasło tylko na swojej domenie', text: 'Rozszerzenie nie przechowuje haseł. Dostaje je na jedno logowanie i wpisuje wyłącznie na domenie wpisu. Przekierowanie na obcą stronę przerywa logowanie.', tag: 'anti-phishing' },
];

const FAQ = [
  { q: 'Czy moje hasła trafiają do internetu?', a: 'Nie. Sejf jest zaszyfrowany na telefonie i na komputerze, a synchronizacja idzie bezpośrednio między nimi w Twojej sieci Wi‑Fi. Nie ma konta ani serwera, przez który dane by przechodziły.' },
  { q: 'Co, jeśli zgubię telefon?', a: 'Komputer możesz odblokować kluczem odzyskiwania (Urządzenia → Utwórz klucz odzyskiwania). Kopię sejfu zapiszesz też do zaszyfrowanego pliku .ostoja.' },
  { q: 'Czy mogę przenieść hasła z Chrome albo Samsung Pass?', a: 'Tak. Ostoja importuje pliki CSV z Chrome, Samsung Pass, Bitwardena i Microsoft Authenticatora, a kody 2FA przeniesiesz z Google Authenticatora (eksport „Przenieś konta”, także kilka kodów QR naraz). Microsoft Authenticator nie pozwala eksportować kodów, więc Ostoja prowadzi przez przepięcie kont po kolei.' },
  { q: 'Dlaczego tylko Firefox?', a: 'Rozszerzenie jest napisane pod Firefoksa i podpisane przez Mozillę. Gdy Firefox jest zamknięty, a logujesz się z telefonu, aplikacja na Windows sama go uruchomi.' },
  { q: 'Ile to kosztuje?', a: 'Nic. Kod jest otwarty na licencji MIT. Możesz go przejrzeć, zbudować samodzielnie albo zgłosić poprawkę.' },
];

export default function Home() {
  return (
    <>
      <nav className="nav">
        <a className="brand" href="#"><Image src={logo} alt="" width={30} height={30} />Ostoja</a>
        <div className="nav-links">
          <a href="#funkcje">Funkcje</a>
          <a href="#firefox">Firefox</a>
          <a href="#bezpieczenstwo">Bezpieczeństwo</a>
          <a href="#kod">Kod</a>
        </div>
        <a className="pill accent small" href="#pobierz"><Ic.Download />Pobierz</a>
      </nav>

      <header className="hero wrap">
        <Reveal><span className="badge"><b>Nowość</b>Logowanie z telefonu otwiera Firefoksa samo</span></Reveal>
        <Reveal delay={60}><h1>Hasła i kody 2FA. <span>Bez chmury.</span></h1></Reveal>
        <Reveal delay={120}>
          <p className="lead">Ostoja to sejf haseł na telefon i komputer z rozszerzeniem do Firefoksa. Urządzenia rozmawiają ze sobą w Twojej sieci, a strona loguje się sama, razem z kodem 2FA.</p>
        </Reveal>
        <Reveal delay={180}>
          <div className="cta">
            <a className="pill accent" href="#pobierz"><Ic.Download />Pobierz za darmo</a>
            <a className="pill fill" href={site.repo}><Ic.Github />Kod na GitHubie</a>
          </div>
          <p className="platforms">Windows 10/11 · Android 9+ · Firefox 140+</p>
        </Reveal>
        <Reveal delay={240} className="stage">
          <div className="window"><Image src={vault} alt="Ostoja na Windows: sejf haseł" priority placeholder="blur" /></div>
          <div className="phone"><Image src={phoneCodes} alt="Ostoja na Androidzie: kody 2FA" priority /></div>
        </Reveal>
      </header>

      <section id="funkcje">
        <div className="wrap">
          <Reveal className="head center">
            <div className="eyebrow">Funkcje</div>
            <h2 className="title">Wszystko, czego potrzebujesz do logowania</h2>
            <p className="sub">Jedna aplikacja zamiast menedżera haseł, aplikacji uwierzytelniającej i karteczki z kodami zapasowymi.</p>
          </Reveal>
          <div className="grid">
            {FEATURES.map((f, i) => (
              <Reveal key={f.title} delay={(i % 3) * 70} className="card feature">
                <div className="icon"><f.icon /></div>
                <h3>{f.title}</h3>
                <p>{f.text}</p>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      <section>
        <div className="wrap split">
          <Reveal>
            <div className="eyebrow">Kody 2FA</div>
            <h2 className="title">Kody liczą się na żywo, tak jak w aplikacji</h2>
            <p className="sub">Te kody obok są prawdziwe: liczy je Twoja przeglądarka tym samym algorytmem (RFC 6238), którego używa Ostoja. Pierścień pokazuje, ile sekund zostało do nowego kodu.</p>
            <ul className="steps">
              <li className="card"><span className="n">1</span><div><b>Zeskanuj kod QR</b><span>Aparatem, z ekranu komputera albo przyciskiem w Firefoksie na stronie włączania 2FA.</span></div></li>
              <li className="card"><span className="n">2</span><div><b>Ostoja połączy kod z hasłem</b><span>Konto GitHub trafi do wpisu GitHub, więc przy logowaniu nie musisz niczego wybierać.</span></div></li>
              <li className="card"><span className="n">3</span><div><b>Kod wpisze się sam</b><span>Na ekranie „podaj kod” rozszerzenie wpisuje go za Ciebie i nie wyśle dwa razy tego samego.</span></div></li>
            </ul>
          </Reveal>
          <Reveal delay={100}><TotpDemo /></Reveal>
        </div>
      </section>

      <section id="firefox">
        <div className="wrap split flip">
          <Reveal>
            <div className="eyebrow">Rozszerzenie do Firefoksa</div>
            <h2 className="title">Wybierz stronę. Zaloguje się sama.</h2>
            <p className="sub">Klikasz wpis w Ostoi na komputerze, w okienku rozszerzenia albo „Zaloguj na komputerze” na telefonie. Firefox otwiera stronę i przechodzi przez całe logowanie.</p>
            <ul className="steps">
              <li className="card"><span className="n"><Ic.Check /></span><div><b>Strona główna bez formularza?</b><span>Rozszerzenie znajdzie „Zaloguj się” i zapamięta adres strony logowania na następny raz.</span></div></li>
              <li className="card"><span className="n"><Ic.Check /></span><div><b>Passkey zamiast kodu?</b><span>Przełączy na „Authenticator app” i wpisze kod z Ostoi.</span></div></li>
              <li className="card"><span className="n"><Ic.Check /></span><div><b>Nowe konto albo zmienione hasło?</b><span>Zapyta, czy zapisać je w Ostoi. Zmiana od razu trafia na telefon.</span></div></li>
            </ul>
          </Reveal>
          <Reveal delay={100}><FirefoxDemo /></Reveal>
        </div>
      </section>

      <section>
        <div className="wrap split">
          <Reveal>
            <div className="eyebrow">Telefon i komputer</div>
            <h2 className="title">Jeden sejf na dwóch urządzeniach</h2>
            <p className="sub">Połączenie ustawiasz raz: komputer pokazuje kod QR, a telefon go skanuje i potwierdza odciskiem palca. Od tej chwili każda zmiana przechodzi w obie strony w kilka sekund.</p>
            <ul className="steps">
              <li className="card"><span className="n"><Ic.Phone /></span><div><b>Android w stylu One UI</b><span>Widżety z kodami 2FA, autouzupełnianie w aplikacjach, blokada biometryczna.</span></div></li>
              <li className="card"><span className="n"><Ic.Windows /></span><div><b>Windows z ikoną przy zegarze</b><span>Startuje z systemem, działa w tle i blokuje sejf co 7 dni do potwierdzenia na telefonie.</span></div></li>
            </ul>
          </Reveal>
          <Reveal delay={100}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, maxWidth: 520, marginLeft: 'auto' }}>
              <div className="phone"><Image src={phoneVault} alt="Sejf na telefonie" /></div>
              <div className="phone" style={{ marginTop: 48 }}><Image src={phoneItem} alt="Wpis z kodem 2FA na telefonie" /></div>
            </div>
          </Reveal>
        </div>
      </section>

      <section id="bezpieczenstwo">
        <div className="wrap">
          <Reveal className="head center">
            <div className="eyebrow">Bezpieczeństwo</div>
            <h2 className="title">Twoje dane zostają u Ciebie</h2>
            <p className="sub">Nie ma serwera, który można włamać, ani konta, które można przejąć. Szyfrowanie jest proste do sprawdzenia, bo cały kod jest jawny.</p>
          </Reveal>
          <div className="sec">
            {SECURITY.map((s, i) => (
              <Reveal key={s.title} delay={(i % 2) * 70} className="card">
                <div className="icon"><s.icon /></div>
                <div><h3>{s.title}</h3><p>{s.text}</p><span className="tag">{s.tag}</span></div>
              </Reveal>
            ))}
          </div>
          <Reveal className="window" delay={80}>
            <Image src={health} alt="Kondycja sejfu w aplikacji na Windows" placeholder="blur" style={{ width: '100%' }} />
          </Reveal>
          <p className="caption">Kondycja sejfu w jasnym motywie. Aplikacja przejmuje kolor akcentu i motyw z telefonu.</p>
        </div>
      </section>

      <section id="pobierz">
        <div className="wrap">
          <Reveal className="head center">
            <div className="eyebrow">Pobierz</div>
            <h2 className="title">Zainstaluj na wszystkich urządzeniach</h2>
            <p className="sub">Za darmo, bez rejestracji. Najpierw komputer, potem telefon i Firefox.</p>
          </Reveal>
          <div className="dl">
            <Reveal className="card">
              <div className="icon"><Ic.Windows /></div>
              <h3>Windows</h3>
              <span className="ver">Wersja {site.versions.windows} · instalator MSI</span>
              <ul>
                <li><Ic.Check />Windows 10 i 11, 64‑bit</li>
                <li><Ic.Check />Aktualizuje poprzednią wersję z zachowaniem danych</li>
                <li><Ic.Check />Ikona przy zegarze i start z systemem</li>
              </ul>
              <a className="pill accent" href={download('Ostoja-Windows.msi')}><Ic.Download />Pobierz dla Windows</a>
              <div className="file">Ostoja-Windows.msi · 60 MB</div>
            </Reveal>
            <Reveal className="card" delay={70}>
              <div className="icon"><Ic.Phone /></div>
              <h3>Android</h3>
              <span className="ver">Wersja {site.versions.android} · plik APK</span>
              <ul>
                <li><Ic.Check />Android 9 lub nowszy</li>
                <li><Ic.Check />Najlepiej wygląda na Samsungach z One UI</li>
                <li><Ic.Check />Wymaga zgody na instalację z pliku</li>
              </ul>
              <a className="pill accent" href={download('Ostoja.apk')}><Ic.Download />Pobierz dla Androida</a>
              <div className="file">Ostoja.apk · 4,5 MB</div>
            </Reveal>
            <Reveal className="card" delay={140}>
              <div className="icon"><Ic.Firefox /></div>
              <h3>Firefox</h3>
              <span className="ver">Wersja {site.versions.firefox} · podpisana przez Mozillę</span>
              <ul>
                <li><Ic.Check />Firefox 140 lub nowszy</li>
                <li><Ic.Check />Działa razem z Ostoją na Windows</li>
                <li><Ic.Check />Kliknij plik w Firefoksie, a zapyta o instalację</li>
              </ul>
              <a className="pill accent" href={download('Ostoja-Firefox.xpi')}><Ic.Download />Dodaj do Firefoksa</a>
              <div className="file">Ostoja-Firefox.xpi · 55 KB</div>
            </Reveal>
          </div>
        </div>
      </section>

      <section id="kod">
        <div className="wrap split">
          <Reveal>
            <div className="eyebrow">Otwarty kod</div>
            <h2 className="title">Sprawdź, jak działa. Linijka po linijce.</h2>
            <p className="sub">Ostoja jest open source na licencji MIT. Aplikacje dzielą jeden moduł w Kotlinie: szyfrowanie, kody 2FA i scalanie sejfów są napisane raz i pokryte testami.</p>
            <div className="tree">
              <div className="card"><code>shared/</code><span>Sejf, TOTP/HOTP, siła haseł, scalanie i protokół synchronizacji</span></div>
              <div className="card"><code>app/</code><span>Aplikacja na Androida (Jetpack Compose)</span></div>
              <div className="card"><code>desktop/</code><span>Aplikacja na Windows (Compose Desktop)</span></div>
              <div className="card"><code>firefox/</code><span>Rozszerzenie: logowanie, kody 2FA, zapisywanie haseł</span></div>
              <div className="card"><code>web/</code><span>Ta strona (Next.js)</span></div>
            </div>
            <div className="stack">
              {['Kotlin', 'Jetpack Compose', 'Compose Desktop', 'WebExtensions', 'AES-GCM', 'ECDH P-256', 'zxing', 'Next.js'].map((t) => <span key={t}>{t}</span>)}
            </div>
            <div className="cta" style={{ justifyContent: 'flex-start' }}>
              <a className="pill accent" href={site.repo}><Ic.Github />Zobacz na GitHubie</a>
              <a className="pill fill" href={`${site.repo}/releases`}>Wszystkie wydania</a>
            </div>
          </Reveal>
          <Reveal delay={100}>
            <div className="card" style={{ overflow: 'hidden' }}>
              <div className="file-tab"><Ic.Code />shared/…/data/Otp.kt</div>
              <pre className="code">
                <span className="c">{'/** RFC 4226 (HOTP) i RFC 6238 (TOTP). */'}</span>{'\n'}
                <span className="k">fun</span> <span className="f">hotp</span>(key: <span className="t">ByteArray</span>, counter: <span className="t">Long</span>, digits: <span className="t">Int</span> = <span className="s">6</span>): <span className="t">String</span> {'{'}{'\n'}
                {'    '}<span className="k">val</span> mac = Mac.getInstance(<span className="s">&quot;HmacSHA1&quot;</span>){'\n'}
                {'    '}mac.init(SecretKeySpec(key, <span className="s">&quot;RAW&quot;</span>)){'\n'}
                {'    '}<span className="k">val</span> h = mac.doFinal(ByteBuffer.allocate(<span className="s">8</span>).putLong(counter).array()){'\n'}
                {'    '}<span className="k">val</span> o = h[h.size - <span className="s">1</span>].toInt() <span className="k">and</span> <span className="s">0x0F</span>{'\n'}
                {'    '}<span className="k">val</span> bin = ((h[o].toInt() <span className="k">and</span> <span className="s">0x7F</span>) <span className="k">shl</span> <span className="s">24</span>) <span className="k">or</span>{'\n'}
                {'        '}((h[o + <span className="s">1</span>].toInt() <span className="k">and</span> <span className="s">0xFF</span>) <span className="k">shl</span> <span className="s">16</span>) <span className="k">or</span>{'\n'}
                {'        '}((h[o + <span className="s">2</span>].toInt() <span className="k">and</span> <span className="s">0xFF</span>) <span className="k">shl</span> <span className="s">8</span>) <span className="k">or</span>{'\n'}
                {'        '}(h[o + <span className="s">3</span>].toInt() <span className="k">and</span> <span className="s">0xFF</span>){'\n'}
                {'    '}<span className="k">var</span> mod = <span className="s">1L</span>{'\n'}
                {'    '}repeat(digits) {'{'} mod *= <span className="s">10</span> {'}'}{'\n'}
                {'    '}<span className="k">return</span> (bin % mod).toString().padStart(digits, <span className="s">&apos;0&apos;</span>){'\n'}
                {'}'}
              </pre>
            </div>
            <div className="window" style={{ marginTop: 14 }}>
              <Image src={codes} alt="Kody 2FA w aplikacji na Windows" placeholder="blur" />
            </div>
          </Reveal>
        </div>
      </section>

      <section>
        <div className="wrap">
          <Reveal className="head center">
            <div className="eyebrow">Pytania</div>
            <h2 className="title">Najczęstsze pytania</h2>
          </Reveal>
          <div className="faq">
            {FAQ.map((f, i) => (
              <Reveal key={f.q} delay={i * 40}>
                <details className="card"><summary>{f.q}</summary><p>{f.a}</p></details>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      <footer>
        <div className="wrap">
          <a className="brand" href="#"><Image src={logo} alt="" width={26} height={26} />Ostoja</a>
          <nav>
            <a href={site.repo}>GitHub</a>
            <a href={`${site.repo}/releases`}>Wydania</a>
            <a href={`${site.repo}/issues`}>Zgłoś problem</a>
            <a href={`${site.repo}/blob/main/LICENSE`}>Licencja MIT</a>
          </nav>
          <span>© 2026 {site.author}</span>
        </div>
      </footer>
    </>
  );
}
