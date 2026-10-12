# Исходный код CCode Android

Это исходный проект приложения CCode 0.2.5.

В репозитории сохранены Kotlin/Compose-интерфейс, JNI-мост, C++-код llama.cpp, настройки Gradle и необходимые native-библиотеки. Папки `build/`, `.gradle/`, IDE-файлы и временные результаты сборки намеренно не включены.

## Сборка

1. Открой папку `android-app` в Android Studio.
2. Установи Android SDK и NDK из настроек проекта.
3. Выполни `./gradlew assembleDebug`.
4. Готовый APK появится в `app/build/outputs/apk/debug/`.

Текущий готовый APK отдельно лежит в [`releases/CCode-0.2.5.apk`](../releases/CCode-0.2.5.apk).
