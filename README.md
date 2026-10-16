# Easy Markdown

Przeglądarka plików **Markdown** z obsługą diagramów **Mermaid**, napisana w
[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/). Pierwsza wersja działa na Androidzie.

## Funkcje

- renderowanie Markdown (nagłówki, listy, tabele, cytaty, bloki kodu, linki) – biblioteka
  [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer),
- diagramy Mermaid z bloków ```` ```mermaid ```` – renderowane przez dołączony lokalnie
  [mermaid.js](https://mermaid.js.org) (działa offline), z jasnym/ciemnym motywem,
- otwieranie plików przez systemowy wybór pliku (**Open**) oraz „Otwórz za pomocą” z innych aplikacji.

## Struktura

```
composeApp/
  src/commonMain   – wspólny UI, podział dokumentu na Markdown i Mermaid
  src/commonTest   – testy jednostkowe parsera
  src/androidMain  – Activity, renderer Mermaid (WebView), assets/mermaid
```

## Budowanie

```bash
./gradlew :composeApp:testDebugUnitTest   # testy
./gradlew :composeApp:assembleDebug       # APK: composeApp/build/outputs/apk/debug/
```

Wymagane JDK 17+ i Android SDK (API 36).

## CI

Workflow [`.github/workflows/android.yml`](.github/workflows/android.yml) przy każdym pushu, PR-ze
lub ręcznym uruchomieniu (*Run workflow*) uruchamia testy i buduje debug APK, które można pobrać
z zakładki **Actions → Artifacts** danego uruchomienia.

Aktualizacja mermaid.js: podmień `composeApp/src/androidMain/assets/mermaid/mermaid.min.js`
(np. `npm pack mermaid` → `package/dist/mermaid.min.js`).
