# CalCalc

A personal Android calorie tracker. You describe or photograph a meal, Gemini turns it into
a table of food items with calorie estimates, you correct it in chat, and press Done to
commit it to your journal.

Not a Play Store app — it is built for one person, signed in with one Google account.

## What it does

- **Onboarding** asks for sex, date of birth, height, weight, how active your day actually
  is, and your weight goal, then computes a daily calorie budget (Mifflin-St Jeor BMR ×
  activity multiplier, adjusted toward the goal).
- **Home** shows today's consumed-vs-target ring and two ways in: write it down, or photograph it.
- **Chat** is where logging happens. The parsed items table stays pinned above the
  conversation; corrections like "make that three eggs" or "drop the fries" update it. Done
  saves the entry.
- **Journal** groups entries by day with a calories chart against your target. Tap an entry
  to reopen it in chat and edit it; add entries to past days.
- **Weight** logs one weight per day, charts the trend, and projects when you reach your goal.
  The latest weight feeds back into the daily target.

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

Two deliberate departures from the original notes:

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
```

The weight document id is the day, so re-weighing corrects rather than duplicates.
