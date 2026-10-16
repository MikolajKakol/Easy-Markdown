# Easy Markdown

Przeglądarka plików **Markdown** z obsługą diagramów **Mermaid**, napisana w
[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/). Pierwsza wersja działa na Androidzie.

<p align="center">
  <img src="docs/screenshots/light_top.png" width="260" alt="Markdown – formatowanie, listy, tabela, kod">
  <img src="docs/screenshots/light_diagrams.png" width="260" alt="Diagramy Mermaid – flowchart i sequence">
  <img src="docs/screenshots/dark_diagrams.png" width="260" alt="Ciemny motyw">
</p>
<p align="center">
  <img src="docs/screenshots/light_fullscreen.png" width="260" alt="Diagram na pełnym ekranie z pinch-to-zoom">
</p>

## Funkcje

- renderowanie Markdown (nagłówki, listy, tabele, cytaty, bloki kodu, linki) – biblioteka
  [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer),
- diagramy Mermaid z bloków ```` ```mermaid ```` – renderowane przez dołączony lokalnie
  [mermaid.js](https://mermaid.js.org) (działa offline), z jasnym/ciemnym motywem,
- tapnięcie diagramu otwiera go na pełnym ekranie z pinch-to-zoom i przesuwaniem,
- otwieranie plików przez systemowy wybór pliku (**Open**), „Otwórz za pomocą” oraz
  **Udostępnij** – aplikacja przyjmuje udostępniane pliki `.md`/tekstowe oraz sam tekst (renderowany jako Markdown).

## Struktura

```
composeApp/
  src/commonMain       – wspólny UI, podział dokumentu na Markdown i Mermaid
  src/commonTest       – testy jednostkowe parsera
  src/androidMain      – Activity, renderer Mermaid (WebView), assets/mermaid
  src/androidUnitTest  – testy snapshot (Roborazzi + Robolectric)
  snapshots/           – wzorcowe obrazy testów snapshot
  debug.keystore       – wspólny klucz debug (APK z CI instalują się jeden na drugim)
docs/screenshots/      – zrzuty z emulatora używane w README
scripts/               – skrypt robiący zrzuty na emulatorze
```

## Budowanie i testy

```bash
./gradlew :composeApp:assembleDebug           # APK: composeApp/build/outputs/apk/debug/
./gradlew :composeApp:testDebugUnitTest       # testy jednostkowe
./gradlew :composeApp:verifyRoborazziDebug    # testy + porównanie snapshotów ze wzorcami
./gradlew :composeApp:recordRoborazziDebug    # nagranie nowych wzorców snapshotów
```

Wymagane JDK 17+ i Android SDK (API 36).

### Testy snapshot

Testy w `AppSnapshotTest` renderują ekran aplikacji w Robolectric i porównują go z obrazami
w [`composeApp/snapshots`](composeApp/snapshots). WebView nie renderuje się pod Robolectric, dlatego
w snapshotach diagramy Mermaid są pokazywane jako ramka z kodem źródłowym – prawdziwe diagramy widać
na zrzutach z emulatora powyżej.

<img src="composeApp/snapshots/sample_light.png" width="260" alt="Snapshot ekranu z przykładowym dokumentem">

## CI (GitHub Actions)

- **Android build** ([`android.yml`](.github/workflows/android.yml)) – przy każdym pushu i PR-ze:
  testy jednostkowe, weryfikacja snapshotów i debug APK do pobrania w **Actions → Artifacts**.
  Uruchomienie ręczne: *Run workflow*.
- **Update screenshots** ([`screenshots.yml`](.github/workflows/screenshots.yml)) – uruchamiane ręcznie:
  nagrywa nowe wzorce snapshotów, robi zrzuty ekranu na emulatorze i commituje je na gałąź,
  na której zostało uruchomione. Użyj po zmianach w UI.

Debug APK podpisywane jest kluczem `composeApp/debug.keystore` (hasło `android`), więc kolejne
buildy z CI można instalować bez odinstalowywania poprzedniej wersji.

Aktualizacja mermaid.js: podmień `composeApp/src/androidMain/assets/mermaid/mermaid.min.js`
(np. `npm pack mermaid` → `package/dist/mermaid.min.js`).
