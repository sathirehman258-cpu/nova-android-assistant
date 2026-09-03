# Nova — Android AI Voice Assistant

An original Android voice assistant (Kotlin, Jetpack Compose, MVVM + Clean Architecture, Hilt,
Room, Retrofit) that understands English, Bangla, and Banglish, and can carry out
user-authorized on-device actions through a strict, validated tool-calling layer.

This is **not** a copy of any commercial assistant's branding, UI, or code — it's an original
architecture built to the same general shape (wake word → STT → intent → confirm → execute →
verify → speak).

## What's implemented and working end-to-end

- **AssistantBrain** (`ai/AssistantBrain.kt`) — the full orchestration pipeline described in the
  spec: STT → language detection → ToolPlanner → risk/confirmation gate → ActionExecutor →
  verification → spoken reply.
- **ToolRegistry + ActionExecutor** — a strict allowlist/schema for every tool. The LLM backend
  only ever returns structured JSON tool calls; anything not in the registry, or with the wrong
  arguments, is rejected before it can execute. This is the core safety boundary.
- **ActionRiskManager** — LOW/MEDIUM/HIGH risk tiers with confirmation gating exactly as spec'd
  (phone calls, sending messages, etc. require a "yes" first; only pre-trusted contacts can
  bypass MEDIUM-risk auto-send).
- **AssistantAccessibilityService** — semantic tap-by-text, coordinate-tap fallback, swipe, type,
  scroll, back/home, and a screen-text reader, with a best-effort refusal to touch screens that
  look like auth/payment/OTP flows.
- **AssistantNotificationListenerService** — local-only notification history; never bulk-uploads
  notification contents.
- **SpeechRecognizerManager** — single-session Android SpeechRecognizer usage (created and
  destroyed per session, not held open) with partial/final results and one retry on timeout.
- **VoiceEngine** — `AndroidTtsVoiceEngine` (fully working fallback) + a `CloudVoiceEngine` stub
  that falls back automatically if a premium provider isn't configured.
- **MemoryManager + Room** — categorized key/value memory with a hard blocklist that refuses to
  store anything that looks like a password/OTP/PIN/card number/token regardless of what's asked.
- **AppManager** — installed-app index built via `ACTION_MAIN`/`CATEGORY_LAUNCHER` queries, which
  avoids requesting the `QUERY_ALL_PACKAGES` permission.
- **PermissionCenter** — progressive, explained permission requests; Accessibility and
  Notification Access are treated as their own explained special-case settings screens.
- **DeviceControls, AutomationManager, VoiceForegroundService** — flashlight/volume/brightness,
  WorkManager-based daily reminders (capped at 20 rules to prevent runaway automations), and a
  foreground service that's only alive during an active listening/response session.
- Compose UI: a single working **Home screen** with a listening orb, mic button, live transcript,
  spoken reply, and inline confirmation card for HIGH-risk actions.
- Unit tests for `ToolRegistry` and `ActionRiskManager`; one instrumentation smoke test.

## What you still need to add (deliberately left as configuration, not code)

1. **A backend.** `BackendApi.kt` defines the contract (`POST /v1/plan`) but there's no server
   here — per the spec, no LLM provider key should ever ship inside the APK. Stand up a small
   backend (any stack) that: authenticates the app, calls your LLM provider with the tool schema
   from `ToolRegistry`, and returns `{ intent, conversationalReply, actions[] }`. Point
   `BACKEND_BASE_URL` in `app/build.gradle.kts` at it.
2. **A real wake-word engine.** `WakeWordManager.kt` is a complete abstraction with the settings
   surface (phrase, sensitivity, battery-saving mode) wired, but the actual "Hey Nova" detection
   requires a licensed/on-device SDK (e.g. Porcupine, or a TFLite keyword-spotting model) — that's
   a deliberate choice per the spec, since Android's `SpeechRecognizer` isn't meant for always-on
   listening. Drop the SDK's init/callback into the two `TODO`s there.
2. **Remaining screens.** Home is fully built; Conversation history, Voice/AI Settings, Memory
   viewer/editor, Automations list, Privacy Center, Connected Services, and About are named in the
   architecture (`ui/settings`, `ui/memory`, `ui/privacy`, etc. folders exist) but only Home has a
   built screen — the ViewModel/data layer underneath (MemoryManager, PermissionCenter,
   AutomationManager) is ready for them.
3. **WhatsApp send flow.** `send_whatsapp_message` is registered and risk-gated, but the actual
   "find the message field, type, tap send" sequence needs to be written against WhatsApp's real
   accessibility tree, which varies by WhatsApp version — do this against a live device/emulator.
4. **Premium/cloud TTS.** `CloudVoiceEngine` is a stub that always falls back to Android TTS;
   wire it to a neural voice provider through your backend if you want a more expressive voice.

## Project layout

```
app/src/main/java/com/nova/assistant/
├── ai/                 AssistantBrain, ToolPlanner
├── voice/              STT, TTS, wake word, foreground service, language detection
├── accessibility/      AssistantAccessibilityService, ScreenContextManager
├── notification/       AssistantNotificationListenerService
├── data/local/         Room (MemoryEntry/Dao/Database)
├── data/remote/        Retrofit backend contract
├── domain/model/       ActionResult, RiskLevel, ToolCall, ParsedIntent
├── domain/tools/       ToolRegistry (schema/allowlist), ActionExecutor, ActionRiskManager
├── domain/usecase/     AppManager, DeviceControls
├── domain/repository/  MemoryManager
├── automation/         AutomationManager (WorkManager-based)
├── permissions/        PermissionCenter
├── di/                 Hilt modules
└── ui/home/            HomeScreen + HomeViewModel (fully working)
```

## Building

1. Open the `nova/` folder in Android Studio (Koala or newer).
2. Let Gradle sync — it targets Android 10+ (minSdk 29), Kotlin 1.9, Compose BOM 2024.06.
3. Set `BACKEND_BASE_URL` in `app/build.gradle.kts` once your backend is deployed.
4. Run on a device/emulator running API 29+. First launch walks through the first-run permission
   flow described in the spec (mic explained before requesting, Accessibility/Notification Access
   explained before sending you to Settings).

## Test commands (from the spec, §32)

Try these once the backend is live — each should be tested in English, Bangla, and Banglish:
"Hey Nova", "Open YouTube", "Call Mom", "Read my notifications", "Turn on flashlight",
"Brightness 50 percent", "Navigate to Mirpur", "Set an alarm for 7 AM", "What's the weather?",
"Forget my name".

## Security & privacy notes baked into the code

- No API keys in the APK — see `BackendApi`/`AppModule` comments.
- `QUERY_ALL_PACKAGES` is intentionally never requested.
- Sensitive-looking memory writes are rejected at the `MemoryManager` layer, not just by prompt
  instruction.
- The accessibility service refuses to act on screens it heuristically flags as auth/payment/OTP.
- HIGH-risk tools (calls, anything sending money/data) always require a spoken "yes" first.

## Build without a PC

This repository includes a GitHub Actions workflow at `.github/workflows/android-build.yml`.
After importing the project into GitHub, open **Actions → Build Nova APK → Run workflow**.
When the run finishes, download the `nova-debug-apk` artifact and extract the APK on your phone.
