package dev.breadcrumb.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookLifecycleTest {
    @get:Rule val leaks = DetectLeaksAfterTestSuccess()

    @Test
    fun releasesRecreatedAndClosedNotebook() {
        ActivityScenario.launch(MainActivity::class.java).use { it.recreate() }
    }
}
