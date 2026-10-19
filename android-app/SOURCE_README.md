# CCode Android source code

This is the source project for the CCode 0.2.5 app.

The repository contains the Kotlin/Compose UI, the JNI bridge, the C++ code llama.cpp, Gradle settings, and the required native libraries. The `build/`, `.gradle/` folders, IDE files, and temporary build outputs are intentionally excluded.

## Build

1. Open the `android-app` folder in Android Studio.
2. Install the Android SDK and NDK from the project settings.
3. Run `./gradlew assembleDebug`.
4. The resulting APK will appear in `app/build/outputs/apk/debug/.`

The current built APK is also available separately at [`releases/CCode-0.2.5.apk`](../releases/CCode-0.2.5.apk).
