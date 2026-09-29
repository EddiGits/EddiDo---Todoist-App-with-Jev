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

- `ai/`: Jev picks the tab, project, labels, priority and reminder as you type.
- `parse/`: code reads dates, times, repeats, durations and list items, in English, Tamil or Hindi.
- `alarm/` rings exact alarms (`setAlarmClock`, shown full screen over the lock screen) or posts notifications, with Done and Snooze buttons. It re-arms repeating tasks and restores alarms after a reboot.

## Tabs and the creator

Eight tabs, each with its own list you can edit: **Tasks** (Today / Upcoming / Inbox / Browse), **Timer**, **Stopwatch**, **Focus** (pomodoro), **Habits** (streaks), **Lists**, **Countdown** and **Notes**.

Tap **+** and the creator grows out of the button in a circle. Type at the bottom. When you pause, Jev picks the tab: the highlight slides there and the preview above changes into what you're making, for example a timer ring filling to 10:00 or list items dropping in one by one. Tab switching works like Shapeshift's: below 40% confidence nothing changes; 40–70% the highlight is dashed; 70%+ it commits; and a different tab must win twice in a row (or be 85%+ sure) to take over. Tap an icon yourself and it stays until you change the text a lot. Nothing is created until you press the accept button.

Animations use Shapeshift's spring values (`ui/motion/Motion.kt`), turned down to quick fades when the phone's "Remove animations" setting is on. Screens are checked with Paparazzi screenshots: `./gradlew recordPaparazziDebug` / `verifyPaparazziDebug`.

## Who decides what

As in Shapeshift: **Jev decides, code computes.** One Jev call per typing pause (~0.4–0.8 s, 20–40 questions answered together). Following Shapeshift's rule ("never ask Jev to extract values or do date math"), Jev is never asked *what date*. It's asked *which kind of day*, and code works out the date (`data/JevReader.kt`):

| Jev chooses | Code computes |
|---|---|
| Which tab (task, timer, habit…) | — |
| Which day: today / tomorrow / day after / a weekday / this weekend / next week / written date / festival | The actual date; festivals from a date table (`parse/Festivals.kt`) |
| Part of day and am/pm | The time, from the digits in the text ("dinner at 8" → 8 PM) |
| Repeat: none / daily / weekdays / weekly / monthly / yearly / hourly | The repeat rule and its interval ("every 2 hours") |
| Unit of a length of time (so "10 nimisham" works) | Seconds |
| For each word: "is this only about when / how / reminding?" | The title: the remaining words |
| Project, priority, labels, reminder, alarm vs notification | — |

The parser (`parse/QuickAddParser.kt`) fills the chips instantly before Jev answers and works offline. Chips you pick yourself always win. `data/TaskResolver.kt` holds the rules for both the live chips and the saved task. Live tests of the real API: `JEV_KEY=… ./gradlew testDebugUnitTest --tests '*JevLiveTest*'`.

**Optional chat model:** set `USE_OPENROUTER=true` in `secrets.properties` and an OpenRouter model also writes titles and reads dates the parser can't ("before Diwali"). It's a second call per pause (~2–5 s) and costs more. `ai/OpenRouterTaskAi.kt` is kept for this.

## Build

1. Copy `secrets.properties.example` to `secrets.properties` (git-ignored) and fill in the OpenRouter key and signing details. The key is compiled into the APK.
2. `./gradlew assembleRelease` (needs JDK 17+ and an Android SDK with platform 35).
3. The APK is written to `app/build/outputs/apk/release/app-release.apk`.

Parser tests: `./gradlew testDebugUnitTest`.

**Keep the release keystore safe.** Android only installs an update over the app if the update is signed with the same key.
