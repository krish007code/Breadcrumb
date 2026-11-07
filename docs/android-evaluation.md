# Evaluate Breadcrumb on Android

Use a USB-connected Android device with debugging enabled. Confirm it appears in `adb devices`. Run the commands below from the repository root. The QA and benchmark builds have separate app IDs and can be installed alongside Breadcrumb.

## Maestro

Install the QA app with `./gradlew :app:installQa`. Start it and tap Continue. To run the two-page edit test, copy the included disposable notebook to a **fresh directory** on the device. For example, run this from a Bash shell:

```sh
fixture="/sdcard/Documents/Breadcrumb-Maestro-$(date +%s)"
adb shell mkdir -p "$fixture"
adb push qa/fixtures/MaestroNotebook/TwoPages "$fixture/"
```

In the QA app, open Settings → Choose folder and select the new directory. Return to Notebook. Then run:

```sh
maestro test .maestro/navigation.yaml
maestro test .maestro/multiple-photos.yaml
```

The first flow opens the overall calendar and returns to the notebook. The second opens the two-page solution, checks the enlarged second photo, long-presses the card, deselects page two in the review dialog and verifies the saved entry has one photo. Seed a fresh folder before repeating the edit test. The initial Android folder permission is a manual step. [Maestro](https://github.com/mobile-dev-inc/Maestro)

## Startup timing

The Macrobenchmark module measures ten cold launches with compilation disabled. Use the same physical device, Android version and starting screen for comparisons. For a clean first-run measurement, clear only the isolated benchmark package before each suite.

```sh
./gradlew :app:installBenchmark
adb shell pm clear dev.breadcrumb.app.benchmark
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Use a physical device running Android 10 or higher. Read the median initial display time in the instrumented test output. Gradle copies the benchmark JSON and traces under `benchmark/build/outputs/connected_android_test_additional_output/`. This test measures startup to the welcome screen. It does not measure camera latency or opening a large notebook. [Android Performance Samples](https://github.com/android/performance-samples)

## Memory leaks

Run `./gradlew :app:connectedQaAndroidTest` to recreate MainActivity, finish it, and have LeakCanary assert that destroyed objects are released. This Gradle task can remove the QA package when it finishes. Set up the disposable folder again before running Maestro if needed.

LeakCanary also starts automatically in the QA build. Watch Logcat with `adb logcat -s LeakCanary`. Open the two-photo fixture, enter and close the fullscreen viewer, rotate the device, then leave the activity using Back. Repeat several times and leave the app running so LeakCanary can check retained objects. If it reports a leak, inspect the reference chain in the QA device's Leaks screen. Heap dumps can contain notebook data, so use the fixture when sharing a report. [LeakCanary](https://square.github.io/leakcanary/getting_started/)

## APK security scan

With Docker running, build the APK you want to inspect, then run:

```sh
./gradlew :app:assembleDebug
qa/mobsf/scan.sh app/build/outputs/apk/debug/app-debug.apk /tmp/breadcrumb-mobsf.json
```

The script runs a pinned MobSF image with networking disabled. It copies the APK into a temporary container, uses the API over loopback inside that container, writes the JSON report locally, and removes the container. The first run downloads the scanner image. A debug APK produces signing and debuggability findings specific to that build. [MobSF](https://github.com/MobSF/Mobile-Security-Framework-MobSF)

### Local scan result

MobSF 4.4.2 scanned Breadcrumb 0.3.0's debug APK, SHA-256 `2eb908c634628b9c32d862af184a35e34e0f4002d70946d6684cbbc7db7a0cbb`. Its score was **46/100**, with two high findings, five warnings and two informational findings. The two high findings are the debug certificate and the debuggable manifest flag. Scan the APK actually being distributed when evaluating a release.

The five warnings cover Android 8 support, Compose's debug preview activity, the AndroidX profile install receiver, potential hardcoded secrets and temporary files. The temporary-file match is in Coil's image decoder. The potential-secret rule matches dependency classes and generated state classes whose fields include `hasSearchKey`; that flag is a Boolean. The clipboard match is the fixed Lens search prompt. The logging match is the OCR exception handler. The scanner used its bundled signatures offline. These results describe static inspection of this APK.

## Device results

On November 7, 2026, both Maestro flows passed on a physical TECNO LH8n running Android 14. The edit flow used a separate QA notebook containing two synthetic PNG pages. It verified full-screen paging, long-press editing, deselecting page two, and the saved one-page result.

The AndroidX benchmark recorded ten cold launches to the welcome screen with compilation disabled. Initial display took **583.7 ms median**, **560.8 ms minimum**, and **718.0 ms maximum**. These values provide a baseline for this handset and build.

The LeakCanary instrumentation test passed after `ActivityScenario` recreated and closed the notebook activity. The assertion covered that lifecycle scenario. Camera capture, Lens handoff and longer editing sessions were outside the test.
