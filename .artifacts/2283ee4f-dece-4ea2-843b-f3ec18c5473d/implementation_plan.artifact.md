# Implementation Plan - Triple-Layer AI Summarization (BYOK + Cascade + Local)

Refactor the AI summarization feature into a robust, three-layer system. This ensures the app is sustainable, provides unlimited access for power users, and always has a fallback.

## The Triple-Layer Strategy

The app will attempt to summarize a page in this exact order:

1.  **Layer 1: User Manual Override (BYOK)**
    *   If a user has entered their own API key (Gemini, OpenAI, Groq, etc.) in Settings, the app uses this **exclusively**.
    *   **Benefit**: Unlimited usage for power users; zero cost for the developer.

2.  **Layer 2: Developer Managed Cascade (Auto-Routing)**
    *   If no user key is provided, the app uses the `ai_routing_cascade` from Remote Config.
    *   It silently tries providers in order (e.g., GROQ -> GEMINI -> DEEPSEEK).
    *   **Benefit**: Highly reliable and free for standard users.

3.  **Layer 3: Graceful Local Fallback (Extractive)**
    *   If all APIs fail (no internet, quota hit, invalid keys), the app performs a **Basic Extractive Summary** on-device.
    *   It picks the first 3 sentences of the main content and adds a disclaimer: "Showing local summary (Online AI busy)".
    *   **Benefit**: The feature **never crashes** and always provides some value.

## Proposed Changes

### [Infrastructure]

#### [MODIFY] [RemoteConfigHelper.kt](file:///Users/user/AndroidStudioProjects/TVNavBrowser/app/src/main/java/com/tdpham/navitvbrowser/util/RemoteConfigHelper.kt)
- Add `ai_routing_cascade`: (String) e.g., `"GROQ,GEMINI,DEEPSEEK"`.
- Add all developer keys: `gemini_api_key`, `groq_api_key`, etc.

#### [REFACTOR] [AiHelper.kt](file:///Users/user/AndroidStudioProjects/TVNavBrowser/app/src/main/java/com/tdpham/navitvbrowser/util/AiHelper.kt)
- **Engine Logic**:
    ```kotlin
    suspend fun getSummary(text: String): String {
        // 1. Try BYOK (User Key)
        // 2. Try Dev Cascade (Remote Config)
        // 3. Perform Local Extractive Summary
    }
    ```
- Implement `LocalExtractiveProvider` that returns the most relevant sentences from the input text.

### [UI & Resources]

#### [MODIFY] [activity_settings.xml](file:///Users/user/AndroidStudioProjects/TVNavBrowser/app/src/main/res/layout/activity_settings.xml)
- Add "AI Configuration" Section:
    - **Mode Selection**: Radio buttons for "Auto-Routing (Default)" or "Manual (BYOK)".
    - **Provider Selection**: (Only visible in Manual) Gemini, OpenAI, Groq, DeepSeek.
    - **Custom Settings**: Endpoint URL (for OpenAI compatible), API Key, and Model ID.

#### [MODIFY] [strings.xml](file:///Users/user/AndroidStudioProjects/TVNavBrowser/app/src/main/res/values/strings.xml)
- Add strings for: `ai_mode_auto`, `ai_mode_manual`, `ai_local_summary_note`, `ai_status_retrying`.

### [Logic]

#### [MODIFY] [MainActivity.kt](file:///Users/user/AndroidStudioProjects/TVNavBrowser/app/src/main/java/com/tdpham/navitvbrowser/MainActivity.kt)
- Update `onSummarizeClicked` to handle the multi-layer result and display status updates if a retry is happening.

## Verification Plan

### Manual Verification
1. **BYOK Test**: Enter a personal key. Verify it skips the dev keys.
2. **Cascade Test**: Delete user key. Set an invalid dev key as #1 in cascade. Verify it retries and succeeds with #2.
3. **Local Test**: Turn off Internet. Verify it shows a "Local Summary" derived from the page text.
