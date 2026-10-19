# CCode — updated Android project

## What's changed

- Added a separate bottom navigation: **Chats / Files / Settings**.
- Chat history opens in a separate screen with search by name, creation, opening, and deletion of chats.
- Added a Files section: name, extension, size, associated chat, content preview and download.
- Export of individual files and ZIP projects preserved.
- Added settings for Code 1.0 / Code 1.0 Lite, web-sources limit, activity display, a fully black default background, and background/accent colors via RGB sliders.
- Added selection of brand badge in the header: CCode, Claude, Gemini and ChatGPT; UI elements use the chosen accent color.
- Added an Info panel with statistics for first message, streak, number of messages, replies and chats.
- Added a separate Markdown export for the current chat; the `.ccode` format for full backups is retained.
- Messages now have a copy button, and created files can be edited and saved back into the project.
- Pin/unpin and rename added in chat history; pinned chats are sorted to the top and persisted between launches.
- Chat history now stores projects and web sources in `filesDir/chats.json`.
- Fixed `.ccode` export: name no longer gets an extra `.json`, import understands full export of chats/projects and the old format.
- Fixed file downloads: original extensions (`.py`, `.gd`, `.html`, `.ccode` and others) are preserved.
- Added guaranteed scaffolds for Godot 4.x and HTML projects with required files and ZIP export.
- Images from the paperclip can be added to the project as binary resources `assets/...` for icons, sprites and other assets; they are included in the ZIP.
- ZIP and APK attachments are unpacked locally into the app's internal storage, displayed in the Files section with a content tree, and persisted between launches.
- When a name or project is mentioned in a new chat, matching saved files are added to the local Code 1.0 context to explain structure and purpose.
- A full `.ccode` transfers history, settings, statistics and saved unpacked projects along with resources.
- An HTML folder with `index.html` gets a 'Build APK' button in the Files section. CCode uses a lightweight local WebView template, places the project in `assets/www/`, creates a signed APK and saves it next to the sources as `Project.apk` or `Project-updated-N.apk`.
- The HTML APK builder does not require Termux and does not download a gigabyte SDK for the first mode: the basic template is built into CCode. Created APKs are signed with a separate CCode Android Keystore key.
- `ccode_jni.cpp`, streaming generation, cancellation, llama.cpp, GGUF, web search, FILE-parser, microphone, notifications and widget preserved.
- Compact white/blue glyph icons in the style of the provided reference are used; the reference image is not used as the app icon.

## Build

Requires Android Studio with Android SDK 35, NDK 26.1 and JDK 21.

```bash
./gradlew assembleDebug
```

APK profile: `app/build/outputs/apk/debug/app-debug.apk`.

The project is built for `arm64-v8a`; native libraries are already located in `app/src/main/jniLibs/arm64-v8a/`.
