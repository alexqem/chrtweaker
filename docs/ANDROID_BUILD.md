# Android App (`mycar/android`) — Build & Release

## Сборка из CLI
```bash
cd android
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew assembleDebug bundleDebug
```

- APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- App Bundle: `android/app/build/outputs/bundle/debug/app-debug.aab`

## Публикация APK на GitHub
Рекомендуется прикреплять собранный `app-debug.apk` (или `app-release.apk`) к **GitHub Releases** (`releases/latest`), а не коммитить бинарник в git-историю. Файлы `*.keystore`, `*.jks`, `local.properties`, `*.apk`, `*.aab` исключены через `.gitignore`.
