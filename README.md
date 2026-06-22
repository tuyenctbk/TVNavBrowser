# Lite Browser for Android TV

Lite Browser is a premium, lightweight, and modern web browser built specifically for the Android TV platform. Designed for "lean-back" television viewing, the application features native remote D-pad optimizations, intelligent focus animations, voice navigation, and advanced visual layouts to make web browsing on TVs feel natural and responsive.

---

## 🌟 Key Features

### 🎙️ Native TV Voice Search
*   Eliminates slow on-screen D-pad keyboard typing.
*   Click the **Voice** button next to the address bar, speak any website name, URL, or search query, and instantly load your page.
*   Uses native Android Speech Recognition for fast and reliable input.

### 🖥️ TV Start Dashboard
*   Loads a clean, dark-themed local startup portal instead of a blank screen.
*   Presents a Google search input and responsive, D-pad friendly shortcut tiles for popular streaming and web destinations (YouTube, Twitch, Wikipedia, IMDb, News, etc.).
*   Fully optimized with hover scaling, shadow glows, and smooth focus-movement transitions.

### 🖱️ Virtual Pointer / Mouse Mode & Gamepad Support
*   Toggle Virtual Mouse Mode using the remote.
*   Use the D-pad arrows to control a visual virtual pointer on screen, and the OK button to select items on pages that do not support native D-pad keyboard navigation.
*   **Game Controller Support**: Navigate the virtual pointer natively using a connected Bluetooth game controller's analog joystick, and click using the `A` or `X` buttons.
*   Automatically supports physical USB or Bluetooth mice when plugged in.

### ⌨️ Smart D-pad Typing Bypass
*   Intelligently detects when the virtual soft keyboard (IME) is active or a text input has focus, and suspends D-pad cursor interception.
*   This allows the user to immediately type on the soft keyboard using the D-pad arrows without moving the mouse pointer.

### ↩️ Loop-Free Focus & Back-Press Escape
*   Overcomes Android TV focus traps. Pressing the remote's BACK button when a text input is focused automatically blurs the HTML element (`document.activeElement.blur()`), closes the keyboard, and shifts focus directly to the toolbar back button (`btnBack`).
*   Ensures users can always exit text inputs and validation tooltips cleanly.

### 🔖 Smart Auto-Hiding Bookmark Bar
*   Displays bookmark collections as horizontal chips underneath the toolbar.
*   **Smart Focus Auto-Hide**: The bar slides open automatically when the user is adjusting settings or address bars. As soon as focus moves back down to browse web content, it collapses cleanly after 2.5 seconds to maximize display view.

### 🛡️ Built-in Web Ad Blocker
*   Integrates an embedded domain-matching blocker that intercepts ad network scripts.
*   Optionally toggle ad blocking in **Settings** to speed up web page load times and keep navigation clutter-free.

---

## 🛠️ Architecture & Technical Stack

-   **Language**: 100% Kotlin utilizing modern Coroutines and Flow APIs.
-   **Local Storage**: Room Database built over SQLite for structured bookmarks and browser history persistence.
-   **Branding & Iconography**: SVG Vector Drawables for crisp, high-resolution rendering at any screen density.
-   **Animations**: Coordinated ObjectAnimators and Android transition APIs for fluid focus-scaling, step-slides, and layout transitions.
-   **Compliance**: Exclusively targets Android TV on Google Play by requiring Leanback hardware features (`android.software.leanback` = true) and marking touchscreen/bluetooth/microphone features as optional.

---

## 🚀 How to Build & Run

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 24+

### CLI Commands
Compile the Kotlin code and verify compilation:
```bash
./gradlew compileDebugKotlin
```

Assemble the debug APK:
```bash
./gradlew assembleDebug
```

Install onto your connected Android TV emulator or physical TV device:
```bash
./gradlew installDebug
```

Launch the application:
```bash
adb shell am start -n com.tdpham.tvnavbrowser/.LauncherActivity
```
