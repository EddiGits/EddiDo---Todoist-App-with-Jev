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

The design follows [Shapeshift](https://github.com/anishfn/shapeshift): **the AI decides, the code computes.**

- `parse/QuickAddParser.kt` reads dates, times, repeats, priority, `#project` and `@labels` in code, offline and instantly. The words it recognises are highlighted as you type.
- `ai/` then sorts the task into a project and adds labels in the background. Anything you typed or picked yourself wins over the AI.
- `alarm/` rings exact alarms (`setAlarmClock`, shown full screen over the lock screen) or posts notifications, with Done and Snooze buttons. It re-arms repeating tasks and restores alarms after a reboot.

## AI providers

| Provider | Job | Speed |
|---|---|---|
| **TypeSafe Jev** (`ai/TypeSafeTaskAi.kt`) | Picks project, labels, priority and alarm-vs-notification in one call | ~0.4 s |
| **OpenRouter** (`ai/OpenRouterTaskAi.kt`) | Reads dates the offline parser can't (Tamil/Hindi, "before Diwali"). It does everything when no TypeSafe key is set | ~2.5 s |
| **Offline** (`ai/OfflineTaskAi.kt`) | Keyword rules, used when both calls fail | instant |

`AiProvider` in `ai/TaskAi.kt` is the only place that picks between them, based on which keys are set in `secrets.properties`. The two calls run side by side, and each answer is applied as soon as it arrives. Jev only *chooses*; dates and times come from the parser in code, as in Shapeshift.

## Build

1. Copy `secrets.properties.example` to `secrets.properties` (git-ignored) and fill in the OpenRouter key and signing details. The key is compiled into the APK.
2. `./gradlew assembleRelease` (needs JDK 17+ and an Android SDK with platform 35).
3. The APK is written to `app/build/outputs/apk/release/app-release.apk`.

Parser tests: `./gradlew testDebugUnitTest`.

**Keep the release keystore safe.** Android only installs an update over the app if the update is signed with the same key.
