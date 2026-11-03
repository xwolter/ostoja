## Pobieranie

| Plik | Dla | Wersja |
|---|---|---|
| `Ostoja.apk` | Android 9+ | 1.2.2 |
| `Ostoja-Windows.msi` | Windows 10/11 | 1.3.1 |

Rozszerzenie do Firefoksa (1.1.8): bez zmian.

## Co nowego
- **Przenoszenie kodów z Google Authenticator działa z nowymi wersjami aplikacji Google.** Kody eksportu zawierające dodatkowe dane kończyły się błędem „out of bounds for length” i żadne konto nie było przenoszone. Teraz nieznane dane są pomijane, a konta wczytują się w całości - także na Windows.
- Polskie znaki w nazwach kont z eksportu wczytują się poprawnie.
- Uszkodzony albo niepełny kod pokazuje zrozumiały komunikat zamiast błędu technicznego.
