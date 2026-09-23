# CalCalc

**Log a meal by describing it or snapping a photo. Gemini does the counting.**

![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?logo=firebase&logoColor=black)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

CalCalc is a personal Android calorie tracker. Type "two eggs, toast with butter and a flat
white" or take a picture of your plate, and Gemini turns it into a table of food items with
calorie and macro estimates. Correct it in plain language ("make that three eggs", "drop the
fries"), press **Done**, and it lands in your journal.

Around that sits a daily calorie budget worked out from your profile and goal, a weight log
that projects when you will reach your target, and an intermittent-fasting tracker with
reminders.

> CalCalc is a personal project, not a Play Store app. There is no shared backend: to run it
> you bring your own Firebase project and Gemini API key (see [One-time setup](#one-time-setup)).
> Calorie estimates come from an AI model and are approximate — this is not medical advice.

**Contents:** [Features](#what-it-does) · [Architecture](#architecture) ·
[Setup](#one-time-setup) · [Build](#build-and-run) · [Data layout](#data-layout) ·
[License](#license)

## What it does

- **Onboarding** asks for sex, date of birth, height, weight, how active your day actually
  is, and your weight goal, then computes a daily calorie budget (Mifflin-St Jeor BMR ×
  activity multiplier, adjusted toward the goal).
- **Home** is a read-only dashboard: today's consumed-vs-target ring, the target breakdown,
  fasting status, and — once anything has been logged — a short read on how the day is
  going. Logging is reached from the bottom bar, not from here.
- **Chat** is where logging happens. The parsed items table stays pinned above the
  conversation; corrections like "make that three eggs" or "drop the fries" update it. Done
  saves the entry. Message text is selectable. A failed send is retried once silently — a
  503 from Gemini is common and usually transient — and only then shows a retry icon.
- **Journal** groups entries by day with a calories chart against your target. Tap an entry
  to reopen it in chat and edit it; add entries to past days. The ↺ to the left of an entry
  copies that meal onto today, since breakfast tends to repeat.
- **Weight** logs one weight per day, charts the trend, and projects when you reach your goal.
  The latest weight feeds back into the daily target.
- **Fasting** runs in one of two modes. *Timer* starts a fast now for 8, 16 or 24 hours —
  picked from clock faces whose arc shows the share of a day — or any custom length; the
  countdown is derived from stored timestamps, so it survives the app being killed.
  *Schedule* is a fixed daily window (say 20:00 → 12:00) that the app evaluates against the
  wall clock, including across midnight. Either mode posts a notification 15 minutes before
  a fast starts and before it ends.

The bottom bar is Profile · Journal · Log · Fasting · Weight, with Home reached by the back
arrow on each screen.

Everything is dark mode only.

## Architecture

| Concern | Choice |
| --- | --- |
| UI | Kotlin + Jetpack Compose, Material 3, Navigation3 |
| Auth | Firebase Auth via Google Sign-In (Credential Manager) |
| Storage | Firestore only — its persistent cache is the offline layer, so there is no local DB |
| AI | Gemini REST (`gemini-3.6-flash`) via Retrofit/Moshi, with a `responseSchema` forcing structured JSON |
| Camera | CameraX in-app capture, plus the system photo picker |
| API key | Encrypted with an Android Keystore AES-GCM key, stored in DataStore |
| Reminders | AlarmManager + a broadcast receiver, with the schedule mirrored to SharedPreferences |

Two deliberate design decisions:

1. **Google Sign-In, not a username/password with a fake domain.** The requirement was "don't
   lose the data when I change phones or reinstall". A fake-domain account has no recovery
   path; a Google account survives both, with no login form to fill in.
2. **Direct Gemini REST rather than the Firebase AI Logic SDK.** They are mutually exclusive:
   AI Logic keeps the key server-side, which leaves no key for the Profile screen to hold.
   The Profile key field won, so the calls go direct.

## One-time setup

The app builds without Firebase but cannot run without it — it shows a setup screen instead
of crashing.

### 1. Firebase project

1. Create a project in the [Firebase console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.example.calcalc`.
3. Get your debug signing SHA-1 and register it on that app (Google Sign-In fails without it):
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
     -storepass android -keypass android | grep SHA1
   ```
4. **Authentication → Sign-in method →** enable **Google**.
5. **Firestore Database →** create a database in native mode.
6. Publish the rules from [`firebase/firestore.rules`](firebase/firestore.rules).
7. Download `google-services.json` into `app/`. It is gitignored on purpose.

### 2. Gemini API key

1. Create a key at [Google AI Studio](https://aistudio.google.com/apikey).
2. In the Google Cloud console, restrict it: **API restriction** → Generative Language API
   only, and an **Android app restriction** bound to `com.example.calcalc` plus the signing
   SHA-1. The key lives on the device, so restricting it is what limits the damage if it leaks.
3. Run the app, open **Profile**, paste the key, and save. It can be changed at any time.

## Build and run

```bash
./gradlew :app:assembleDebug     # build
./gradlew :app:installDebug      # install on a connected Android 15+ device
./gradlew :app:testDebugUnitTest # unit tests
```

`minSdk` is 35 (Android 15).

## Data layout

```
users/{uid}                        profile: sex, birthDate, heightCm, weightKg,
                                   activityLevel, goal, goalWeightKg, goalDate
users/{uid}/entries/{autoId}       dateLocal, createdAt, updatedAt, items[],
                                   totalCalories, source
users/{uid}/weights/{yyyy-MM-dd}   weightKg, recordedAt
users/{uid}/fasting/config         mode, dailyStartMinute, dailyEndMinute
users/{uid}/fasting/active         startedAt, plannedEndAt (absent when not fasting)
users/{uid}/fasts/{autoId}         startedAt, endedAt, plannedEndAt
```

Daily fasting times are minutes from local midnight, not timestamps: "I stop eating at 8pm"
is a wall-clock rule and must not drift with dates or timezones.

The weight document id is the day, so re-weighing corrects rather than duplicates.

## Notifications

Fasting reminders are the only thing that needs the notification permission, so it is asked
for on the Fasting screen, and only once a mode is switched on.

Alarms outlive the process, so the fasting settings are mirrored into SharedPreferences: a
receiver waking at 19:45, or after a reboot, has to know the schedule without signing in to
Firestore first. Each firing arms the next one.

Exact alarms need a permission Android will not grant on its own; without it the reminders
fall back to `setAndAllowWhileIdle`, which the system may delay by a few minutes. That is
fine for a fifteen-minute warning. To make them exact, grant "Alarms & reminders" in the
app's system settings.

## Day insight

`domain/NutritionInsight.kt` is what Home shows under the entries. It is time-aware on
purpose: every line is judged against the share of the day's budget a normal eating pattern
would have used by that hour, and shortfalls stay quiet until mid-afternoon. A
breakfast-only morning should not be told it is missing dinner.

Macro advice is skipped entirely unless most of the day's calories carry that macro — Gemini
leaves them null when it cannot estimate, and totalling only the items that have numbers
would understate the day.

## License

Released under the [MIT License](LICENSE).
