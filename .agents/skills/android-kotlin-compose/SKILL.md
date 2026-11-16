---
name: android-kotlin-compose
description: >-
  Native Android development skill for amaMomoApp using Kotlin, Jetpack Compose,
  Room, Coroutines, Retrofit, and Android Telephony/Accessibility USSD automation.
  Use when building, architecting, debugging, or testing the Android client.
---

# Android Kotlin & Jetpack Compose (amaMomoApp)

Build and maintain the native Android mobile money agent application (`amaMomoApp`) using modern Android architecture.

## Stack & Architecture

- **Language & UI:** Kotlin, Jetpack Compose (Material 3), Navigation Compose.
- **Async & Networking:** Kotlin Coroutines, Flow, Retrofit + OkHttp.
- **Local Persistence (Offline-First):** Room (SQLite) for local transaction logs, cached network configs, and sync queue.
- **DI & State:** ViewModel + StateFlow unidirectional data flow.

## USSD Automation & Platform Guardrails

1. **No Hardcoded USSD Trees:** Load network menu definitions, USSD codes, and regex matchers from configurable definitions rather than scattering codes through UI/ViewModel classes.
2. **State-Machine Driven:** Advance USSD steps only after observing and matching the expected prompt; never guess menu options blindly.
3. **Permission & Platform Respect:**
   - Use Android Telephony APIs (`TelephonyManager.sendUssdRequest`) where supported.
   - Use Android Accessibility Service only with explicit user opt-in and clear disclosure.
   - Fall back to user-assisted interaction flows when a device/OS/SIM/carrier restricts programmatic USSD access.
4. **PIN & Secret Safety:** Never send agent/customer mobile money PINs to the backend, and never embed Paystack Secret Keys in the Android app.

## Verification

- Run `./gradlew test` for unit tests (state machine, regex parsers, Room DAOs, ViewModels).
- Run `./gradlew lint` and `./gradlew assembleDebug` to verify compilation and lint checks before completing Android changes.
