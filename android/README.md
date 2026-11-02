# ZCode for Android

Open ZCode on the phone. **Chats** opens the project and conversation drawer. **Files** opens the local workspace, where you can create folders and files, edit text, and import or export documents using the Android picker. **Terminal** opens an interactive Bash PTY in the same directory. Git and ripgrep are bundled.

The chat, tool approvals, model selection, settings, projects and task history come from the sibling ZCode source. Use **Manage models → Add provider** for an upstream preset or **Create custom provider**. Set its URL, API format, key and model ID. The configurable wire formats are OpenAI Chat Completions, OpenAI Responses, and Anthropic Messages. Compatible local inference servers also work if reachable from the phone. The upstream welcome dialog offers **Use API key → Skip for now** to enter the workspace before configuring a provider.

No desktop server or ADB connection is required at runtime. A foreground service keeps the workspace running when you switch apps. To shut it down, use the notification’s **Close workspace** action or remove ZCode from Recents. The app and its shell share the Android app UID. Files elsewhere can be imported through the document picker. The mobile build uses the upstream Web client; desktop-only integrations such as Electron window automation, desktop browser control and platform-specific plugins are not portable as-is.

## Build and install

Place this directory beside the ZCode checkout. On macOS install Node, npm, Python 3, JDK 8 (javac), a current JDK for Android build tools, Android SDK 34 and build-tools 34.0.0, and an NDK with the ARM64 API 26 compiler. From this directory run:

```sh
./build.sh
adb install --no-incremental -r build/zcode.apk
```

The build stages the client and local host from source, compiles the agent, and packages Termux ARM64 binaries and an Android PTY helper. Keep build/debug.jks to update an existing development installation. This is a locally signed development APK using target SDK 28 to execute packaged binaries in private storage.
