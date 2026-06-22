# Google Play Store Listing Materials - NaviTV Browser

This document contains the metadata, descriptions, graphics checklist, and configuration details required to publish **NaviTV Browser** on the Google Play Store for Android TV.

---

## 📝 Store Listing Metadata

### 1. App Title (Max 30 characters)
> **NaviTV Browser: TV Web Browser**

### 2. Short Description (Max 80 characters)
> **Browse the web on your Android TV with your remote, voice search, & mouse mode.**

### 3. Full Description (Max 4,000 characters)
```text
Experience web browsing designed exclusively for the big screen with NaviTV Browser! Built from the ground up for Android TV, NaviTV Browser delivers a fast, secure, and lean-back web experience that you control entirely using your standard TV remote.

Whether you want to stream videos, check the news, browse social media, or access your favorite web apps, NaviTV Browser makes navigation simple and seamless without requiring a touchscreen.

🌟 KEY FEATURES:

• TV-Optimized Navigation (D-Pad)
Move naturally between buttons, links, and forms using your remote's arrow keys. The active elements light up with clear focus highlights so you never lose track of your cursor.

• Voice Search & Dictation
Skip tedious typing on an on-screen keyboard. Simply click the voice search button, speak your search query or URL, and watch NaviTV load it instantly using advanced Android speech recognition.

• Customizable Startup Dashboard
Get to your favorite sites faster. Add, delete, and customize speed dial shortcuts directly on your home screen. Pre-configured with TV-friendly links to streaming, news, and search portals.

• Virtual Mouse Pointer & Touchpad Mode
For complex websites, toggle Mouse Mode to control a smooth virtual pointer using your remote control's directional keys. Also supports connected physical USB or Bluetooth mice, touchpads, and keyboards.

• Game Controller Support
Play and navigate with your gamepad! Use your Bluetooth game controller's analog joystick to glide the virtual pointer and click using the controller buttons.

• Website Ad Blocker
Enjoy a cleaner, faster web. Toggle our built-in ad blocker in Settings to filter intrusive banners and pop-up scripts, saving bandwidth and improving page load times.

• Smart Focus Auto-Hide
Our auto-hiding bookmark bar appears only when you focus on the address bar or settings. Once you scroll down to enjoy web content, it moves out of your way to maximize the screen view.

• Privacy First
Your browsing history and bookmarks are saved securely on your device. Easily clear your cache, history, or cookies at any time from our simple Settings screen.

Set your own custom homepage, bookmark unlimited sites, and enjoy the web on your television today with NaviTV Browser!
```

---

## 🎨 Graphical Assets Checklist (Android TV Specific)

To publish on Android TV, Google Play has unique graphical requirements that differ from mobile listings:

| Asset Type | Required Dimensions | Format | Notes / Requirements |
| :--- | :--- | :--- | :--- |
| **App Icon** | 512 x 512 px | PNG (32-bit) | Must be vibrant, clear, and look good against dark TV backgrounds. |
| **Feature Graphic** | 1024 x 500 px | PNG or JPEG | Displays at the top of the store page. Showcase the browser running on a TV frame. |
| **TV App Banner** | 1280 x 720 px (16:9) | PNG or JPEG | **CRITICAL FOR TV**: This is displayed on the Android TV / Google TV home launcher. Must include the app logo. |
| **TV Screenshots** | 1920 x 1080 px (16:9) | PNG or JPEG | Minimum of 1 screenshot is required. Recommended: 3-5 showing Dashboard, Web Page loading, Settings, and Mouse Mode in action. |

---

## 📋 Console Setup & Questionnaire Answers

When filling out the App Content section in the Google Play Console, use these guidelines:

### 1. Target Audience and Content
*   **Target Age Group**: 18 and older (or 13+ depending on your regional preference). Since it is a general-purpose web browser, restricting to older age groups simplifies child safety policies.
*   **Contains Ads**: **Yes** (Since AdMob banners are integrated at the bottom of the main activity).

### 2. Android TV Compatibility Declaration
In the Play Console, go to **Release > Setup > Advanced settings > Device categories** and add **Android TV**.
*   **Declaration Checklist**:
    *   Confirm the app does not require a touchscreen (`android.hardware.touchscreen` is set to `false`).
    *   Confirm the app requires Leanback hardware (`android.software.leanback` is set to `true`).
    *   Confirm the app displays a launcher banner (`android.intent.category.LEANBACK_LAUNCHER` is declared).

### 3. Privacy Policy Link
Google Play requires a public URL for your Privacy Policy.
*   **Privacy Policy Link**: You can host the contents of `PRIVACY_POLICY.md` on a GitHub Pages link or your website.
    *   Example URL: `https://tuyenctbk.github.io/TVNavBrowser/privacy-policy.html`

---

## 📢 Release Notes / What's New (Supported Languages)

Below are the release notes for the Play Console release submission, kept within Google Play's 500-character limit:

### 🇺🇸 English (EN)
```text
• Initial release of NaviTV Browser!
• Full D-pad remote optimization with smart focus.
• Voice Search for quick web queries.
• Customizable Dashboard speed dial shortcuts.
• Virtual mouse pointer and gamepad support.
• Built-in website ad blocker toggle.
• Full Firebase Analytics and Cloud Messaging integrations.
• Modern dark theme styling and performance improvements.
```

### 🇪🇸 Spanish (ES)
```text
• ¡Lanzamiento inicial de Navegador NaviTV!
• Optimización completa del control remoto D-pad con enfoque inteligente.
• Búsqueda por voz para consultas rápidas.
• Accesos directos personalizables en el panel de inicio.
• Soporte para puntero de ratón virtual y control de juego.
• Bloqueador de anuncios web integrado.
• Integración completa con Firebase Analytics y Cloud Messaging.
• Diseño de tema oscuro y mejoras de rendimiento.
```

### 🇵🇹 Portuguese (PT)
```text
• Lançamento inicial do Navegador NaviTV!
• Otimização completa do controle remoto D-pad com foco inteligente.
• Pesquisa por voz para consultas rápidas.
• Atalhos personalizáveis no painel de início.
• Suporte para ponteiro de mouse virtual e controle de jogo.
• Bloqueador de anúncios de sites integrado.
• Integração completa com Firebase Analytics e Cloud Messaging.
• Visual em tema escuro e melhorias de desempenho.
```

### 🇻🇳 Vietnamese (VI)
```text
• Phiên bản đầu tiên của Trình duyệt NaviTV!
• Tối ưu hóa hoàn toàn cho điều khiển D-pad với tiêu điểm thông minh.
• Tìm kiếm bằng giọng nói để duyệt tìm nhanh chóng.
• Phím tắt trang chủ tùy chỉnh linh hoạt.
• Hỗ trợ chuột ảo và tay cầm chơi game.
• Tích hợp chặn quảng cáo trang web.
• Hỗ trợ đầy đủ Firebase Analytics và Cloud Messaging.
• Giao diện tối hiện đại và tối ưu hiệu năng.
```

### 🇫🇷 French (FR)
```text
• Lancement de Navigateur NaviTV !
• Optimisation complète de la télécommande directionnelle (D-pad).
• Recherche vocale pour des requêtes web ultra-rapides.
• Raccourcis personnalisables sur le tableau de bord de démarrage.
• Pointeur de souris virtuel et prise en charge des manettes.
• Bloqueur de publicités intégré.
• Intégration complète de Firebase Analytics et Cloud Messaging.
• Thème sombre moderne et optimisations de performance.
```

### 🇩🇪 German (DE)
```text
• Erster Release des NaviTV Browsers!
• Volle D-Pad Fernbedienungs-Optimierung mit Fokus-Highlights.
• Sprachsuche für schnelle Web-Abfragen.
• Anpassbare Schnellwahl-Favoriten auf dem Start-Dashboard.
• Virtueller Mauszeiger und Unterstützung für Gamepads.
• Integrierter Werbeblocker für Webseiten.
• Komplette Firebase Analytics und Cloud Messaging Integration.
• Elegantes Dark-Mode-Design und Leistungsverbesserungen.
```
