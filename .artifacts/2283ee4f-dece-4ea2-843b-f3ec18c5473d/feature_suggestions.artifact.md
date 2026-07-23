# Feature Suggestions & Improvement Roadmap for NaviTV Browser

Based on the [SERVICES_REGISTRY.md](file:///Users/user/AndroidStudioProjects/TVNavBrowser/SERVICES_REGISTRY.md) and current TV browser trends, here is a list of recommended features and architectural improvements.

## 1. AI-Enhanced Browsing (The "Agentic" Browser)
> [!TIP]
> Use the **Google Gemini API** (Generative AI) to transform the browser from a simple viewer into a smart assistant.

- **AI Search & Intent Parsing**: Instead of raw keywords, allow users to say "Find me the best-rated sci-fi movies on Netflix" and have the browser navigate directly to the results or a curated list.
- **Smart Summarization**: Add a "Summarize Page" button in the toolbar. This is extremely useful on TV where reading long articles is difficult. Use Gemini to extract the 3-5 key points.
- **Voice Commands 2.0**: Move beyond basic search. Implement commands like "Scroll down," "Open my bookmarks," or "Switch to dark mode" using **Wit.ai** or Gemini for intent classification.

## 2. Dynamic Dashboard & Content Discovery
- **Live Widgets**: Use **Open-Meteo** (no key required) to add a weather widget and **NewsAPI** for a rolling news ticker on the `dashboard.html`.
- **Media Metadata (TMDB Integration)**: When a user bookmarks a movie or TV show site, use [TMDB](https://developer.themoviedb.org/) to fetch high-resolution posters and ratings, replacing generic favicons with rich media cards.
- **Trending Shortcuts**: Automatically populate a "Trending" section on the dashboard using **Google Trends** or **YouTube Data API**.

## 3. Utility & Accessibility
- **Page Translation**: Integrate the **DeepL API** or **MyMemory API** to offer one-click translation of foreign websites.
- **QR Code Sharing**: Add a "Send to Phone" feature that generates a QR code of the current URL using **QR Server API**.
- **Enhanced Ad Blocking**: Augment the existing `EmbeddedAdBlocker` with a community-maintained filter list (like EasyList) fetched via **GitHub API** periodically.

## 4. Infrastructure & Reliability (Multi-Cloud)
> [!IMPORTANT]
> To increase uptime and user data safety, adopt the "Bedrock" strategy from the registry.

- **Supabase as Secondary Backend**: Use **Supabase** (PostgreSQL) as a secondary sync provider for Bookmarks and History. If Firebase is unreachable or restricted, the app can fall back to Supabase.
- **Device-to-Device Sync**: Use **Pusher** or **Ably** to allow users to "push" a URL from their phone browser directly to their TV without typing.
- **On-Device ML**: Use **LiteRT (formerly TFLite)** for on-device page classification to automatically enable "Reading Mode" or "Video Mode" based on page content.

## 5. Monetization & Engagement
- **Native Ad Layouts**: Continue using **AdMob Native Ads** but design specialized layouts that mimic TV app "Recommendations" or "Suggested Channels" to make them less intrusive.
- **Remote Config for UI**: Use **Firebase Remote Config** to A/B test different dashboard layouts or shortcut orders without requiring an app update.

---

### Recommended Free APIs for Immediate Integration
| Service | Use Case | Free Tier |
| :--- | :--- | :--- |
| **Google Gemini** | AI Summary / Voice Intent | Generative AI Studio (Free) |
| **Open-Meteo** | Dashboard Weather | No Key Required / Open |
| **NewsAPI** | Dashboard Headlines | 100 requests/day (Free) |
| **TMDB** | Movie/TV Metadata | Free with Attribution |
| **QR Server** | Share to Phone | Free / Unlimited |
| **DeepL** | Page Translation | 500,000 chars/month |
