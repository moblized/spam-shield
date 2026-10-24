# Spam Shield

An Android app that screens incoming calls and silences or hangs up on spam.

## Install

Open the [latest release](../../releases/latest), download **SpamShield.apk** on your phone and install it.
Then open the app and tap **Turn on**, which makes Spam Shield your phone's *Caller ID & spam app*.
(On Samsung: Settings → Apps → Choose default apps → Caller ID & spam app.)

Every push to `main` builds a new signed APK through GitHub Actions; it installs over the old one and keeps your lists and settings.

## What it catches

Each rule can be set to **Off**, **Silence** (rings quietly, still shows on screen) or **Block** (hangs up; the call stays in your call history).

| Rule | Default |
| --- | --- |
| Your block list (numbers and patterns like `800*`, `*1234`, `212555????`) | Block |
| Reported to the FTC (Do Not Call complaints, 2+ reports in the last 30 days) | Block |
| Failed caller ID check (carrier STIR/SHAKEN verification failed) | Block |
| Neighbor spoofing (same area code + first 3 digits as your number) | Silence |
| One-ring scam area codes (876, 809 and other Caribbean codes) | Silence |
| Private or hidden numbers | Silence |
| International calls | Off |
| PhoneBlock community reports (online lookup, mostly European numbers) | Off |
| Everyone not in your contacts | Off |

Your contacts and the allow list always ring.

## How it works

- Android's `CallScreeningService` hands the app every incoming call from a number that isn't in your contacts. No dialer replacement and no call-log permission needed.
- The FTC publishes the numbers people report to the Do Not Call registry every weekday as CSV files (`https://www.ftc.gov/sites/default/files/DNC_Complaint_Numbers_YYYY-MM-DD.csv`). The app downloads the last 7–90 days to the phone once a day, so checking a call never sends the number anywhere.
- PhoneBlock lookups (`https://phoneblock.net/phoneblock/api/num/{number}`) are the only network call made while a phone is ringing, and only if you turn that rule on.

Calls only — texts aren't touched, because Android only lets the default texting app block messages.

## Code

Kotlin + Jetpack Compose, Room, DataStore and WorkManager.

- `core/` — pure Kotlin: number formatting, wildcard patterns, the rules engine and the FTC CSV parser (unit tested in `app/src/test`).
- `screening/ScreeningService.kt` — answers Android's call-screening request.
- `data/` — database, settings, FTC download, PhoneBlock client.
- `ui/` — Home, Rules, Lists and Settings screens.
