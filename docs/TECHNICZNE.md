# Ostoja - jak to działa w środku

Szczegóły techniczne dla osób, które chcą zrozumieć albo sprawdzić kod. Krótki opis projektu jest w [README](../README.md).

## Synchronizacja telefon ↔ komputer
1. Na komputerze otwórz Ostoję. Przy pierwszym uruchomieniu pokazuje kod QR (później jest w zakładce Urządzenia).
2. Na telefonie wejdź w Ustawienia → Połącz z komputerem, zeskanuj kod i potwierdź odciskiem palca.
3. Od tej chwili wpisy, kody 2FA, zmiany haseł i usunięcia przechodzą w obie strony w kilka sekund. Komputer przejmuje z telefonu kolor akcentu i ustawienia ikon.

Jak to działa:
- Kod QR niesie losowy 32-bajtowy sekret. Oba urządzenia wyprowadzają z niego klucz AES-256-GCM (HKDF-SHA256) i nim szyfrują oraz uwierzytelniają każde zapytanie i odpowiedź. Sekret nigdy nie idzie przez sieć.
- Zapytania mają znacznik czasu i numer jednorazowy, więc przechwyconego zapytania nie da się powtórzyć.
- Każde urządzenie trzyma pełny sejf. Przy spotkaniu stany są scalane: dla każdego wpisu wygrywa nowsza wersja, a usunięcia przenoszą „nagrobki”.
- Telefon łączy się z komputerem na porcie 47811. Gdy komputer zmieni adres IP, telefon odnajduje go rozgłoszeniem UDP na porcie 47812.
- Zmiany i prośby z komputera telefon odbiera przez długie zapytanie. W tle robi to cicha usługa „Połączono z komputerem”, którą można wyłączyć w Ustawieniach → „Prośby logowania od razu”. Bez niej synchronizacja działa tylko przy otwartej Ostoi.

## Logowanie na komputerze z potwierdzeniem na telefonie
Co 7 dni (oraz po „Zablokuj”) Ostoja na Windows blokuje sejf:
1. Na komputerze kliknij „Potwierdź na telefonie”. Komputer pokaże liczbę.
2. Na telefonie przyjdzie powiadomienie „Czy to Ty logujesz się na komputerze?”. Wybierz tę samą liczbę spośród trzech i dotknij czytnika linii papilarnych.
3. „To nie ja” wylogowuje komputer.

Poprawną liczbę zna tylko komputer, więc nie da się zatwierdzić prośby „na ślepo”. Gdy telefonu nie ma pod ręką, komputer odblokuje też klucz odzyskiwania (Urządzenia → Utwórz klucz odzyskiwania).

## Rozszerzenie Firefox: wybierz stronę, a zaloguje się sama
Plik `Ostoja-Firefox.xpi` (źródła w `firefox/`) działa razem z aplikacją Ostoja na Windows.

Logowanie:
1. Klikasz ikonę Ostoi (albo naciskasz Ctrl+Shift+O), wybierasz wpis i naciskasz Enter albo strzałkę.
2. Strona otwiera się w nowej karcie, a Ostoja sama:
   - wpisuje login;
   - przy logowaniu dwuetapowym klika „Dalej” i czeka na pole hasła;
   - wpisuje hasło i wysyła formularz;
   - wpisuje świeży kod 2FA, także w 6 osobnych pól.
3. Postęp widać w małej pigułce w rogu strony.

Dodawanie haseł:
- Gdy logujesz się ręcznie, Ostoja pyta „Zapisać hasło w Ostoi?”. Przy zmienionym haśle pyta „Zaktualizować hasło do …?”. Do wyboru są też „Nie teraz” i „Nigdy dla tej strony”.
- W okienku rozszerzenia jest przycisk „+” i wiersz „Dodaj hasło dla <strona>”: formularz z generatorem silnego hasła.
- W aplikacji na Windows jest „Nowy wpis” (login, karta, notatka, Wi‑Fi), a przy każdym wpisie ikony edycji i usuwania.
- Wszystko od razu trafia też na telefon.

Kody 2FA:
- Na stronie włączania 2FA z kodem QR (np. GitHub → Settings → Password and authentication) Ostoja pyta „Dodać ten kod 2FA do Ostoi?”. Ten sam przycisk jest w okienku rozszerzenia: „Dodaj kod 2FA z tej strony”. Kod jest odczytywany ze zrzutu karty przez aplikację i od razu łączy się z hasłem do tego serwisu.
- W aplikacji na Windows „Dodaj konto 2FA” → „Zeskanuj ekran” szuka kodu QR na monitorach. Można też wkleić zrzut albo przepisać klucz.
- Na ekranie „podaj kod 2FA” Ostoja sama wpisuje kod, jeśli ma go dla tej strony, także gdy hasło wpisał Firefox albo Ty.

Strona logowania:
- Wpis otwiera adres z pola „Strona logowania” (w aplikacji: Edytuj, na telefonie: edycja wpisu), np. `https://github.com/login`.
- Gdy wpis ma samą domenę, a na stronie głównej nie ma formularza, rozszerzenie samo klika „Zaloguj się” (link do /login, /logowanie albo okienko logowania). Znaleziony adres zapisuje we wpisie, więc następnym razem otwiera od razu formularz.
- Rozszerzenie nie wpisuje loginu w pola rejestracji ani newslettera na stronie głównej.

Inne sposoby:
- „Wypełnij” albo Ctrl+Shift+L wypełnia stronę, która jest już otwarta. Ikona rozszerzenia pokazuje, ile wpisów pasuje do bieżącej strony.
- W aplikacji na Windows przycisk „Otwórz i zaloguj w Firefoksie” jest w szczegółach wpisu.
- Na telefonie przycisk „Zaloguj na komputerze” jest we wpisie. Strona otwiera się i loguje w Firefoksie na komputerze. Gdy Firefox jest zamknięty, aplikacja Ostoja sama go uruchamia i loguje po starcie (aplikacja musi działać, np. w ikonie obok zegara).

Bezpieczeństwo:
- Rozszerzenie niczego nie zapisuje w przeglądarce. Hasło przychodzi z aplikacji tylko na czas jednego logowania i jest wpisywane wyłącznie na domenie wpisu. Przekierowanie na obcą stronę przerywa logowanie.
- Parowanie odbywa się przez wymianę kluczy ECDH P-256. Ten sam 6-cyfrowy kod widać w rozszerzeniu i w oknie aplikacji, a połączenie zatwierdzasz przyciskiem „Połącz”. Dalej każde zapytanie jest szyfrowane AES-256-GCM.
- Strony internetowe nie mogą udawać rozszerzenia, bo aplikacja przyjmuje tylko zapytania od rozszerzeń przeglądarki.

**Instalacja.** Zwykły Firefox instaluje na stałe tylko podpisane dodatki. Do wyboru:
- Na próbę: `about:debugging` → Ten Firefox → „Załaduj tymczasowy dodatek…” → wybierz `Ostoja-Firefox.xpi`. Dodatek działa do zamknięcia Firefoksa.
- Na stałe, za darmo: zaloguj się na addons.mozilla.org → Developer Hub → „Submit a New Add-on” → „On your own”, czyli dodatek niepubliczny. Wgraj `Ostoja-Firefox.xpi` i pobierz podpisany plik. Podpis przychodzi automatycznie po kilku minutach.
- Firefox Developer Edition albo Nightly: w `about:config` ustaw `xpinstall.signatures.required` na `false` i przeciągnij `.xpi` do okna.

Potem kliknij ikonę Ostoi → „Połącz” i zatwierdź ten sam kod w oknie aplikacji.

## Ostoja na Windows
- Ekrany Sejf, Kody 2FA, Kondycja i Urządzenia, a w nich:
  - kategorie i panel szczegółów;
  - siła hasła i „Generuj nowe hasło” z długością 16/20/24;
  - pierścienie kodów;
  - kapsuła na dole, pasek tytułu po przewinięciu, toasty i wyszukiwarka (Ctrl+K).
- „Dodaj konto 2FA”: link `otpauth://`, sam klucz, kod QR ze schowka (zrzut Win+Shift+S) albo z pliku.
- Zamknięcie okna chowa aplikację do zasobnika, a synchronizacja działa dalej. W menu ikony jest „Uruchamiaj z Windows”.
- Dane leżą w `%APPDATA%\Ostoja\ostoja.bin`, szyfrowane AES-256-GCM kluczem chronionym przez Windows (DPAPI, tylko to konto użytkownika).

## Animacje
- Kapsuła ma 4 zakładki: Sejf, Kody, Skaner i Ustawienia. Akcentowa pigułka przesuwa się po krzywej `cubic-bezier(.34,1.3,.64,1)` (300 ms, z lekkim przestrzeleniem). Wybrana zakładka rozszerza się do 120 dp i pokazuje podpis.
- Przy przewijaniu w dół kapsuła opada o 12 dp i zmniejsza się do 86%, a przy przewijaniu w górę wraca. Na skanerze zjeżdża i znika.
- Duży tytuł znika z paralaksą (`1 − y/110`, przesunięcie `y·0,25`). Mały tytuł pojawia się pośrodku paska.
- Pierścienie kodów płynnie się opróżniają, a przez ostatnie 5 s są czerwone. Chip „Nowe kody za N s” odlicza czas do zmiany kodów.
- Przyciski i karty po wciśnięciu pomniejszają się do 0,96 i odbijają (`cubic-bezier(.34,1.5,.64,1)`). Przełączniki mają sprężysty kciuk (280 ms).
- Toast wjeżdża z dołu z odbiciem i omija kapsułę. Skaner po odczytaniu kodu zaciska ramkę i zmienia kolor narożników na akcent.

## Co jest w środku
| Część | Opis |
|---|---|
| Sejf | loginy, karty, notatki i sieci Wi‑Fi; kategorie, wyszukiwarka i „Kondycja sejfu” (słabe, powtórzone hasła i hasła w wycieku); dotknięcie otwiera wpis, a przytrzymanie kopiuje hasło |
| Bezpieczeństwo | wynik 0-100 liczony ze słabych, powtórzonych i starych haseł |
| Szczegóły / edycja | pokazywanie hasła, siła hasła, historia 5 poprzednich haseł, powiązany kod 2FA na żywo |
| Generator | długość 8-64, grupy znaków, pomijanie podobnych znaków (l/I/1/O/0) |
| Kody 2FA | TOTP/HOTP, SHA1/256/512, 6-8 cyfr, okres 30/60 s; pierścień odliczania i podgląd następnego kodu |
| Skaner | CameraX + ZXing; odczyt z aparatu albo ze zrzutu ekranu; obsługuje `otpauth://` i eksport z Google Authenticator (`otpauth-migration://`) |
| Ustawienia | motyw, 4 kolory akcentu, chowanie kapsuły, blokada biometryczna, auto-blokada, czyszczenie schowka, blokada zrzutów ekranu |
| Widżety | „Ostoja · kody” (4×2), duży kod (2×2, z wyborem konta), kafel „Skanuj kod QR” (2×2), „Kondycja sejfu” (4×1), Szybki dostęp (4×1) |
| Powiadomienia | kod przypięty na 3 minuty, z odliczaniem i przyciskiem „Kopiuj” (Live Update / Now Bar); „Ostoja · autouzupełnianie”, czyli kod 2FA po wpisaniu hasła; „Hasło X wyciekło” |
| Autouzupełnianie | podpowiada loginy w aplikacjach i przeglądarkach (po biometrii); proponuje zapis nowych haseł |
| Dodatki | kafelek w szybkich ustawieniach, skróty na ikonie (skanuj, nowe hasło, kody), linki `otpauth://` |

## Bezpieczeństwo
- `vault.bin` jest szyfrowany AES-256-GCM kluczem z Android Keystore (TEE/Knox), więc pliku nie da się odczytać na innym urządzeniu.
- Kopia zapasowa `.ostoja`: PBKDF2-SHA256 (310 tys. iteracji) + AES-256-GCM, chroniona hasłem użytkownika.
- Kopia w chmurze jest wyłączona. Do przeniesienia danych służy eksport z hasłem albo własny komputer z Ostoją (sieć lokalna, szyfrowanie end-to-end).
- Synchronizacja z komputerem idzie zwykłym HTTP w sieci domowej, ale każda wiadomość jest zaszyfrowana i uwierzytelniona kluczem z parowania (`shared/.../sync/Protocol.kt`).
- Internet jest używany tylko do dwóch rzeczy: pobierania ikon serwisów (sama domena, można to wyłączyć) i sprawdzania wycieków w Have I Been Pwned. Do HIBP trafia tylko 5 znaków skrótu SHA-1 (k-anonimowość), więc samo hasło nigdy nie opuszcza telefonu.
- Skopiowane hasła są oznaczane jako wrażliwe (One UI nie pokazuje ich w podglądzie schowka) i czyszczone po 30 s.
- Na Windows skopiowane hasła i kody mają znaczniki, po których system nie zapisuje ich w historii schowka (Win+V) ani w schowku w chmurze.
- Granica domeny uwzględnia sufiksy publiczne (`github.io`, `vercel.app`, `waw.pl`, `com.pl`…; `shared/.../data/Domains.kt`), więc hasło do `twojprojekt.github.io` nie trafi na `obcy.github.io`.
- Rozszerzenie nie wpisuje hasła na stronie `http://`, gdy wpis jest dla `https://`. Hasła i listę wpisów dostaje tylko okienko rozszerzenia, nigdy skrypt na stronie.
- Autouzupełnianie na Androidzie wierzy adresowi strony tylko w przeglądarkach. Zwykła aplikacja (rozpoznawana po nazwie pakietu, którą da się podrobić) zawsze dostaje pytanie z nazwą pakietu.
- Lokalny serwer na komputerze odrzuca zapytania ze stron internetowych (nagłówki Host, Origin i Sec-Fetch-Site), więc strona nie zamknie Ostoi ani nie dobierze się do rozszerzenia przez DNS rebinding.

## Import
Ustawienia → Importuj przyjmuje: kopię Ostoi, CSV z Chrome / Google Password Manager / Samsung Pass / Bitwarden albo plik tekstowy z linkami `otpauth://`.

## Budowanie
Potrzebny jest JDK 17+ i Android SDK (ścieżka w `local.properties`: `sdk.dir=...`).
```
gradlew :app:assembleRelease
gradlew :desktop:packageMsi
gradlew :shared:test :desktop:test
```
- APK: `app/build/outputs/apk/release/app-release.apk`.
- Instalator: `desktop/build/compose/binaries/main/msi/`.
- Strona projektu: `cd web && npm install && npm run build` (statyczny eksport w `web/out`).
- Wersja bez instalacji (`gradlew :desktop:createDistributable`): `desktop/build/compose/binaries/main/app/Ostoja/Ostoja.exe`.

Testy w `shared` sprawdzają:
- wektory RFC 4226/6238, Base32, parser otpauth i migracji, CSV oraz generator haseł;
- scalanie sejfów (przemienność, nagrobki, duplikaty 2FA), odrzucanie zmienionych lub podrzuconych wiadomości i kod parowania.

`desktop:test` zapisuje zrzuty ekranów aplikacji na Windows w `desktop/build/snapshots`.

## Logo
`design/`: `icon.svg`, `icon-1024.png`, `logo.png`, `logo-dark.png`. Ikona aplikacji jest adaptacyjna i ma warstwę monochromatyczną (motyw ikon One UI).
