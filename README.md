# EddiDo: a Todoist-style Android app with natural-language tasks

Type a task in plain words and EddiDo works out the rest:

| You type | You get |
|---|---|
| `call mom tomorrow 7pm` | "Call mom", tomorrow 7:00 PM, notification |
| `wake me up every weekday 6am` | "Wake up", ringing alarm Mon–Fri at 6:00 |
| `10 min timer` | Ringing alarm in 10 minutes |
| `pay rent on 5th p1 #finance @home` | P1, Finance project, label `home`, due on the 5th |
| `gym every mon, wed and fri 6:30am` | Repeats on those days |
| `buy milk, eggs and bread` | The AI files it under Shopping and adds the `groceries` label |

The AI side is inspired by [Shapeshift](https://github.com/anishfn/shapeshift). Here the AI decides as much as it can, including dates, and the code keeps an offline copy of each rule as a backup.

- `ai/` reads the whole task as you type: Jev picks the project, labels, priority and reminder, and a language model writes the title and works out the date, time and repeat, in English, Tamil or Hindi.
- `parse/QuickAddParser.kt` fills the chips instantly while the AI is thinking, and is the backup when there is no internet.
- `alarm/` rings exact alarms (`setAlarmClock`, shown full screen over the lock screen) or posts notifications, with Done and Snooze buttons. It re-arms repeating tasks and restores alarms after a reboot.

## Tabs and the creator

Eight tabs, each with its own list you can edit: **Tasks** (Today / Upcoming / Inbox / Browse), **Timer**, **Stopwatch**, **Focus** (pomodoro), **Habits** (streaks), **Lists**, **Countdown** and **Notes**.

Tap **+** and the creator grows out of the button in a circle. Type at the bottom. When you pause, Jev picks the tab: the highlight slides there and the preview above changes into what you're making, for example a timer ring filling to 10:00 or list items dropping in one by one. Tab switching works like Shapeshift's: below 40% confidence nothing changes; 40–70% the highlight is dashed; 70%+ it commits; and a different tab must win twice in a row (or be 85%+ sure) to take over. Tap an icon yourself and it stays until you change the text a lot. Nothing is created until you press the accept button.

Animations use Shapeshift's spring values (`ui/motion/Motion.kt`), turned down to quick fades when the phone's "Remove animations" setting is on. Screens are checked with Paparazzi screenshots: `./gradlew recordPaparazziDebug` / `verifyPaparazziDebug`.

## Who decides what

The AI decides everything it can. The app's own code only fills the chips instantly while the AI is thinking, and stands in when there is no internet.

| Field | Decided by | Fallback (offline / while waiting) |
|---|---|---|
| Title (date words removed, typos fixed) | Language model (OpenRouter) | Parser |
| Date, time, repeat | Language model | Parser |
| Reminder on/off, alarm vs notification | **Jev** (if ≥50% confident), else language model | Parser |
| Project | **Jev**; `#name` typed by you via the language model | Keyword rules |
| Priority | **Jev**; `p1`–`p4` / "urgent" typed by you via the language model | Parser |
| Labels | **Jev** (7 yes/no questions) + language model (`@label` and suggestions) | Parser (`@label`) |
| Which tab (task, timer, habit…) | **Jev** (24/24 right in testing) | Keyword rules |
| Timer length, list items, habit times per day, countdown date | Language model | Parser |
| Ringing the alarm at the right moment | App (Android `AlarmManager`), since the AI can't do this | — |

Chips you pick yourself always win. `data/TaskResolver.kt` holds these rules in one place. The live chips and the saved task both use it, so they always match. Safety checks: a date from the AI that is in the past is ignored, and a date you clearly typed is kept if the model misses it.

| Provider | File | Speed |
|---|---|---|
| TypeSafe Jev (`TYPESAFE_API_KEY`) | `ai/TypeSafeTaskAi.kt` | ~0.4 s |
| OpenRouter (`OPENROUTER_API_KEY`), `typesafe/jev-router`, DeepSeek as backup | `ai/OpenRouterTaskAi.kt` | ~2–5 s |
| Offline keyword rules | `ai/OfflineTaskAi.kt` | instant |

## Build

1. Copy `secrets.properties.example` to `secrets.properties` (git-ignored) and fill in the OpenRouter key and signing details. The key is compiled into the APK.
2. `./gradlew assembleRelease` (needs JDK 17+ and an Android SDK with platform 35).
3. The APK is written to `app/build/outputs/apk/release/app-release.apk`.

Parser tests: `./gradlew testDebugUnitTest`.

**Keep the release keystore safe.** Android only installs an update over the app if the update is signed with the same key.
