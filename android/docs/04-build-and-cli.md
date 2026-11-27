# Building & Headless CLI Execution

## Prerequisites
- **JDK:** OpenJDK 17 (`JAVA_HOME=/opt/homebrew/opt/openjdk@17`).
- **Android SDK:** API 34 + Build Tools 34.0.0 (`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`).
- No Android Studio installation required.

---

## 1. Native CLI Build Commands

From the `chr/android` directory:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

# 1. Compile Debug APK
./gradlew assembleDebug

# 2. Compile Debug Android App Bundle (.aab)
./gradlew bundleDebug

# 3. Compile Release APK
./gradlew assembleRelease

# 4. Clean artifacts
./gradlew clean
```

### Artifact Paths
- **Debug APK:** `app/build/outputs/apk/debug/app-debug.apk`
- **Debug Bundle:** `app/build/outputs/bundle/debug/app-debug.aab`

---

## 2. Device Installation via ADB

Connect your phone with USB debugging enabled:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. Headless Docker Build

Build inside an isolated container with zero host dependencies:

```bash
# Build custom builder image
docker build -t chr-android-builder .

# Run build and output APK to host
docker run --rm -v $(pwd):/app chr-android-builder
```

Alternatively, use a pre-built community image:
```bash
docker run --rm -v $(pwd):/project -w /project alvr/alpine-android:latest ./gradlew assembleDebug
```
