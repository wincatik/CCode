# CCode

**CCode — local Android AI assistant for mobile coding.**

The project helps create complete source files and small multi-file projects directly on your phone. CCode works with local models Code 1.0 and Code 1.0 Lite based on Qwen2.5-Coder.

## Current version — 0.2.6

APK 0.2.6 contains:

- chats and conversation history;
- sections **Chats**, **Files**, and **Settings**;
- dark minimalist interface;
- local models **Code 1.0 (3B)**, **Code 1.0 Lite (1.5B)**, **Code 1.0 Balanced (1.5B)**, and **Code 1.0 Fast (0.5B)**;
- in-app model downloads;
- generation of full files, not only code snippets;
- support for Python, Godot 4.x / GDScript 2.0, and other text source files;
- creation of multi-file projects with a project card;
- downloading individual files;
- export project to ZIP;
- export chat to `.ccode` format;
- attaching and analyzing scripts, ZIP archives, and APK files;
- web documentation search with page content download and sources;
- Manus-style activity panel: request analysis, file planning, search, writing, and verification;
- stopping generation with an X button;
- streaming response output;
- statistics of messages, chats, and activity;
- chat history;
- voice input via microphone;
- notifications;
- activity widget with streak;
- optimizations for devices with 4 GB of RAM.

> In 0.2.6 an experimental HTML/APK Builder was added for local preparation of APK projects.

## Download

- [Download CCode 0.2.6 APK](https://github.com/wincatik/CCode/releases/download/v0.2.6/CCode-0.2.6.apk)
- [View all Releases](https://github.com/wincatik/CCode/releases)

To install the APK, allow installation of apps from unknown sources in Android settings. This is a signed release build for publication on RuStore. The previous version 0.2.5 is preserved in a separate release.

## Website

- [Official CCode website](https://wincatik.github.io/CCode/)

## Source code

- [GitHub repository](https://github.com/wincatik/CCode)
[Full Android project source](https://github.com/wincatik/CCode/tree/main/android-app)
[Issues page](https://github.com/wincatik/CCode/issues)

The source project is stored in the `android-app` folder. It includes a Kotlin/Compose UI, a C++/JNI bridge, Gradle settings, and the native libraries required for building. Temporary folders `build/` and `.gradle/` are intentionally excluded.

## Technical foundation

- Kotlin and Jetpack Compose;
- C++17 and JNI;
- llama.cpp;
- GGUF models Qwen2.5-Coder;
- Android NDK/SDK;
- Gradle 8.7.

## Authors

- **Creator of CCode:** wincatik
- **AI co-author and technical assistant:** Manus AI

## License

The project's license will be added prior to public release. Until then, the source code is provided for review.
