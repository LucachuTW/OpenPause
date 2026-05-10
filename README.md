# OpenPause ⏸️

<p align="center">
  <img src="docs/images/logo.png" alt="OpenPause Logo" width="150" height="150"/>
</p>

<p align="center">
  <em>A production-oriented, open-source Android app blocker designed to promote digital wellbeing and mindful smartphone usage.</em>
</p>

## 📖 About OpenPause

OpenPause is built to break the cycle of unconscious phone usage. Instead of strictly blocking an application—which often leads to frustration and uninstallation—OpenPause uses psychological friction:
- **Mandatory Wait Time**: When you attempt to open a distracting app, you are presented with a countdown overlay. You must wait before entering. This breaks the automatic reflex of opening addictive apps.
- **Session Limits**: If you stay in the app for too long, the pause screen will reappear to break your scrolling loop and remind you of the time passed.

### Features
- List launchable installed apps.
- Enable customized restriction rules per app (Wait Time & Session Limit).
- Lightweight and battery-efficient foreground app detection using `AccessibilityService`.
- Clean UI built with Material Design 3 and Jetpack Compose.

## 🏗 Architecture

The app follows a pragmatic **Clean Architecture + MVVM** split:
- `domain`: Models, repository contracts, and use cases.
- `data`: Room entities/DAO/database and Android package catalog implementation.
- `presentation`: Compose screens, Material 3 theme, and ViewModels.
- `service`: Accessibility interception and overlay lifecycle management.
- `di`: Hilt bindings and database providers.

*The UI renders immutable state from ViewModels. The accessibility service keeps enabled rules in memory to ensure foreground events do not hit the database on every window transition.*

## 🛠️ How to Compile & Install

If you want to build the APK from source, follow these steps:

### Prerequisites
1. **Java Development Kit (JDK) 21**: Make sure you have JDK 21 installed.
   - On Ubuntu/Debian: `sudo apt install openjdk-21-jdk`
   - On macOS (Homebrew): `brew install openjdk@21`
2. **Android SDK**: Ensure `compileSdk 36` is available in your Android SDK.

### Build Instructions
Clone the repository and run the Gradle wrapper to build the Debug APK:

```bash
git clone https://github.com/yourusername/OpenPause.git
cd OpenPause

# On Linux / macOS
./gradlew assembleDebug

# On Windows
gradlew.bat assembleDebug
```

If successful, your APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### ⚠️ Important: Installation & "Restricted Settings" (Android 13+)
Because you are installing an APK from outside the Google Play Store (sideloading), Android 13+ will block sensitive permissions like Accessibility and "Display over other apps" for security reasons.

If you see a **"Restricted Settings"** error when granting permissions, do the following:
1. Go to your phone's **Settings** > **Apps** > **OpenPause**.
2. Tap the **three dots** in the top right corner.
3. Select **"Allow restricted settings"** (Authenticate if asked).
4. Go back to OpenPause and grant the Accessibility and Display permissions normally.

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! 
Feel free to check the issues page if you want to contribute. Please follow standard Android development guidelines when submitting PRs.

## 📄 License

This project is open-source under the terms of the LICENSE included in this repository.
