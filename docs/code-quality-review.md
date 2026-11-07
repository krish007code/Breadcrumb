# Code quality review

Evaluated on 7 November 2026 using Detekt 1.23.8 and Compose Rules 0.4.23. The reviewed source commit is `86112a8241eed083cf910457ea34d82856935c10`.

## Results

| Measure | First scan | After initial fixes |
| --- | --- | --- |
| Total findings | 272 | 254 |
| Compose findings | 14 | 4 |

These counts describe static-analysis findings requiring review. The configured rules check production and JVM test source.

## Fixes included

- Added root modifier parameters to the public app, progress, and welcome composables, and a default modifier to the stat tile.
- Grouped each screen's emitted UI under one root layout and grouped the overview and calendar sections consistently.
- Added explicit cancellation catch blocks before user-facing exception handling in coroutine operations.
- Added a diagnostic log when scan recognition fails.
- Used ordinary loops for calendar ranges.

## Remaining findings

| Rule | Count |
| --- | --- |
| `MaxLineLength` | 145 |
| `MagicNumber` | 55 |
| `TooGenericExceptionCaught` | 12 |
| `WildcardImport` | 11 |
| `CyclomaticComplexMethod` | 7 |
| `LongMethod` | 5 |
| `ViewModelForwarding` | 4 |
| `ComplexCondition` | 4 |
| `TooManyFunctions` | 3 |
| `ReturnCount` | 3 |
| `ThrowsCount` | 3 |
| `LongParameterList` | 2 |

## Suggested next work

1. Hoist screen state and callbacks from the ViewModel into screen-specific state. The four remaining Compose findings identify the app's screen-dispatch calls.
2. Split long screen functions into cohesive components and separate notebook, editor, and scan responsibilities in the ViewModel. Review URL parsing and photo import/save complexity alongside the existing regression tests.
3. Review broad exception catches. Storage cleanup and UI boundaries have different recovery needs, so each catch should be assessed in context.
4. Address long lines, wildcard imports, and unexplained numeric values. Most current findings are in these style rules.

## Representative locations

- **ViewModelForwarding** — Forwarding a ViewModel/Presenter through multiple @Composable functions should be avoided. Consider using state hoisting. [Source](https://github.com/krish007code/Breadcrumb/blob/86112a8241eed083cf910457ea34d82856935c10/app/src/main/java/dev/breadcrumb/app/BreadcrumbApp.kt#L166)
- **LongMethod** — The function AddScreen is too long (92). The maximum length is 60. [Source](https://github.com/krish007code/Breadcrumb/blob/86112a8241eed083cf910457ea34d82856935c10/app/src/main/java/dev/breadcrumb/app/BreadcrumbApp.kt#L322)
- **TooManyFunctions** — Class 'NotebookViewModel' with '32' functions detected. Defined threshold inside classes is set to '11' [Source](https://github.com/krish007code/Breadcrumb/blob/86112a8241eed083cf910457ea34d82856935c10/app/src/main/java/dev/breadcrumb/app/NotebookViewModel.kt#L28)
- **CyclomaticComplexMethod** — The function normalize appears to be too complex based on Cyclomatic Complexity (complexity: 20). Defined complexity threshold for methods is set to '15' [Source](https://github.com/krish007code/Breadcrumb/blob/86112a8241eed083cf910457ea34d82856935c10/app/src/main/java/dev/breadcrumb/app/ProblemLinks.kt#L32)
- **TooGenericExceptionCaught** — The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled. [Source](https://github.com/krish007code/Breadcrumb/blob/86112a8241eed083cf910457ea34d82856935c10/app/src/main/java/dev/breadcrumb/app/NotebookViewModel.kt#L176)

## Run the analysis

```bash
./gradlew :app:detekt
```

Reports are generated under `app/build/reports/detekt/` in HTML, Markdown, XML, and SARIF formats. The HTML report includes findings, source excerpts, and complexity metrics.

Detekt currently returns a failing exit status for the open findings. Its default Gradle integration also attaches analysis to `check`. Existing findings are included in the reports and the quality gate.

For a combined run that completes the other checks even when Detekt reports findings, use the following command.

```bash
./gradlew :app:detekt testDebugUnitTest lintDebug assembleDebug --continue
```

## Verification

The APK compiles and all 24 JVM tests pass. Android lint completes with zero errors and 22 warnings. The combined Gradle command exits nonzero because Detekt reports the 254 open findings. Device testing of the changed layout grouping remains pending.

## Configuration references

The dependency versions follow the published compatibility matrix. The naming configuration follows Detekt's Compose conventions.

- [Compose Rules compatibility and setup](https://mrmans0n.github.io/compose-rules/detekt/)
- [Detekt Compose configuration](https://detekt.dev/docs/1.23.8/introduction/compose/)
