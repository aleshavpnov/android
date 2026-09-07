# Alesha Vpnov для Android

Одна кнопка «Включить». Подписка добавляется из кабинета в Telegram по ссылке
`https://durov.aimuzov.online/app/add#u=…` (или `aleshavpnov://add?u=…`), VPN поднимается сам.
Ядро — libXray (Xray-core) с встроенным TUN-инбаундом; дескриптор от `VpnService` передаётся
через `env.xray.tun.fd`. Дизайн: `docs/superpowers/specs/2026-09-07-android-client-design.md`.

## Окружение (macOS)

```bash
mise use -g java@temurin-21
brew install --cask android-commandlinetools
export ANDROID_HOME=$HOME/Library/Android/sdk
sdkmanager --sdk_root=$ANDROID_HOME --install "platform-tools" "platforms;android-37.2" \
  "ndk;27.2.12479018" "emulator" "system-images;android-35;google_apis;arm64-v8a"
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.2.12479018
go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init
```

Gradle читает `ANDROID_HOME` из окружения либо `sdk.dir` из `local.properties` (в git не входит).

## Сборка

```bash
npm run core -w @aleshavpnov/android      # libXray.aar → app/libs (один раз на версию ядра)
npm run build:debug -w @aleshavpnov/android
npm run test:jvm -w @aleshavpnov/android
```

Версия берётся из `-PversionName=X.Y.Z` (CI ставит по тегу `android-vX.Y.Z`), локально — `0.0.0`.

## Релиз

Тег `android-vX.Y.Z` запускает `.github/workflows/android.yml`: сборка ядра (кэш по тегу
libXray), Gradle, юнит-тесты, подпись ключом из secrets, публикация `app-release.apk` и
`version.json` в `aimuzov/aleshavpnov-releases`. Secrets: `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `RELEASES_PAT`. Отпечаток ключа
(`keytool -list -v -keystore release.jks`) — в env Caddy `APP_CERT_SHA256` на Timeweb, иначе
App Link не верифицируется.

## Проверка на устройстве

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -a android.intent.action.VIEW -d 'aleshavpnov://add?u=<base64url ссылки>'
adb logcat -s Alesha
adb shell pm verify-app-links --re-verify online.aimuzov.aleshavpnov
```
