# Notebook code quality

| Check | First audit | Before follow-up | After follow-up |
| --- | ---: | ---: | ---: |
| Detekt findings, including Compose | 272 | 254 | 3 |
| Compose findings | 14 | 4 | 0 |

The follow-up split the edit, settings, solution, notebook and calendar screens into focused components. Those screens now receive state and actions from the app. Storage operations share rollback handling for incomplete writes, and URL cleanup uses rules for each provider. The Detekt configuration and thresholds used for the first audit still apply.

## Open findings

| Source | Rule | What remains |
| --- | --- | --- |
| NotebookViewModel.kt | TooManyFunctions | One ViewModel coordinates persisted drafts, camera imports, OCR, search, navigation and notebook writes. |
| BreadcrumbApp.kt | LongMethod | The root composable binds screen actions and dispatches navigation. |
| BreadcrumbApp.kt | CyclomaticComplexMethod | Screen selection, draft replacement and navigation decisions still share the root composable. |

`./gradlew :app:detekt` exits nonzero with these findings. The HTML and SARIF reports are under `app/build/reports/detekt/`. To complete the unit tests, lint and assembly in the same run, use:

```sh
./gradlew :app:detekt :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --continue
```

See the [initial audit](https://github.com/krish007code/Breadcrumb/blob/ec30617/docs/code-quality-review.md) and the [Android evaluation guide](android-evaluation.md).
