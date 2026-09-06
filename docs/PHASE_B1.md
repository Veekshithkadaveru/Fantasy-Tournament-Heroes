# Phase B1 — Zeus, Trial of Strength

The launcher opens the playable Zeus trial while Phase C tournament navigation is pending. Choose Qualifier, Semifinal, or Final, then begin. Hold the gold control to charge; release when the blue power line reaches the moving gold band. Its bright central fifth awards a perfect strike.

## Gameplay

- Five throws per round; perfect = 200, band hit = 130, miss = 0; maximum 1,000.
- The bundled configuration supplies round duration (20/20/25 seconds), throw counts, band widths, and drift speeds.
- `bandStart` and `bandEnd` are the first and last throw's band widths (24% to 10%); widths interpolate across throws. Semifinal scales widths by 0.88 and Final by 0.76. Drift uses the configured 1.0/1.4/1.8 speed factors.
- Full charge takes 1.45 seconds and stays at 100% until released. Each strike has 0.8 seconds of feedback. The trial ends after the fifth strike's feedback or when the timer expires. Unused throws award no points.
- Begin starts the timer. Pause, app backgrounding, and activity recreation preserve the current in-memory run and cancel an active charge without consuming a throw. Resume is explicit. A fresh process starts a new run, as specified by the plan.
- Touch cancellation never submits a strike. Keyboard Space/Enter works as hold/release. Accessibility activation toggles charge/release. Haptics respect the stored vibration preference.
- Zeus trial replay does not increment tournament records or completed tournament runs. Tournament persistence and navigation are integrated in Phase C; audio and final ceremony remain in Phase D.

## Presentation

The UI uses a midnight temple backdrop, a gold and electric-blue palette, serif display lettering, animated embers, breathing hero art, a live meter, a five-strike history, and a contextual charge button. Perfects produce double lightning and a brief low-opacity gold flash. Hits produce blue lightning; misses produce a fizzle and ground mark. Score popups, particles, and impact rings are drawn with Compose Canvas. The existing temple and gem textures live in `drawable-nodpi` to prevent Android from inflating their decoded size on high-density phones; their resource names and image content are unchanged.

The layout respects system insets, caps its width on larger displays, and scrolls on short or landscape screens. The engine is pure Kotlin; the ViewModel loads config off the main thread and accepts elapsed time from the lifecycle-aware Compose frame clock.

## Artwork

Built-in imagegen was used to create `app/src/main/res/drawable-nodpi/zeus_arena.png`, using `zeus_portrait.png` as the edit target. The original assets are preserved. The new image removes the baked checkerboard by placing the same character in a storm environment.

Final prompt:

> Use case: compositing. Asset type: hero artwork for an Android fantasy timing game. Edit target: the supplied Zeus portrait. Preserve Zeus's face, white hair, gold laurel, golden armor, blue cloth, pose and electric-blue lightning, in the same polished illustrated game style. Replace every gray checkerboard area with a real atmospheric midnight-navy storm background: faint ancient Greek temple pillars and deep blue mist, subtle gold embers, no gray checkerboard. Full bleed square composition, Zeus centered, upper torso and head dominant, face in upper middle. Edges and lower torso blend naturally into deep midnight blue shadows (#080f21) for a dark game UI. No transparency, no text, no logos, no borders, no interface elements. Produce a beautiful high-quality professional fantasy game hero illustration.

## Verification

The user has chosen to confirm gameplay and visuals manually and requested no further testing. B1 implementation is marked complete in the development plan. The checks below are historical results, not a request for additional test runs.

Implementation is complete. Debug app and instrumentation APK builds pass; all 21 local unit tests pass (16 Zeus engine tests and 5 existing tests). Android lint reports 0 errors and 62 existing project warnings, with no warnings in the Zeus implementation or text resources.

Device UI execution and visual inspection remain pending. The saved Android 12 emulator repeatedly restarted its system services, including watchdog failures and a lost network stack. The app and test APKs installed, and a direct activity launch returned success, but the instrumentation runner stalled before reporting any tests and UI inspection timed out. The stalled runner was stopped; this is not a passing UI-test run. The final attempt to launch an isolated emulator with temporary data was declined. Temporary display size and density overrides were reset, the temporary stay-awake setting was turned off, and the emulator launched for this task was closed. No saved emulator data was wiped.

Final verified command:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest
```

Installable build: `app/build/outputs/apk/debug/app-debug.apk`.

`ZeusEngineTest` covers score boundaries, all three rounds, five perfect strikes, difficulty scaling, movement bounds, duplicate input, cancellation, pause/resume, timeout, reset, invalid deltas, and frame-rate independence.

`ZeusTrialScreenTest` exercises actual pointer down/up across recomposition, pointer cancellation, pause/resume, timeout/replay, and difficulty selection with a controlled simulation clock.

Commands:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.krafted.fantasyheroestournament.trial.zeus.ZeusTrialScreenTest
```
