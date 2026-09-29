package com.eddigits.eddido.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.eddigits.eddido.data.AppStore
import com.eddigits.eddido.data.TaskRepository
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.ui.theme.EddiDoTheme
import org.junit.Rule
import org.junit.Test

/** The real home screen and creator, rendered with the app's own data classes. */
class FullScreenTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 0.1)

    @Test fun home() {
        val repo = TaskRepository.get(paparazzi.context)
        val store = AppStore.get(paparazzi.context)
        paparazzi.snapshot {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                EddiDoTheme { EddiDoApp(repo, store) }
            }
        }
    }

    @Test fun creator() {
        val repo = TaskRepository.get(paparazzi.context)
        val store = AppStore.get(paparazzi.context)
        paparazzi.snapshot {
            EddiDoTheme { Composer(Kind.TASK, repo, store, TaskRepository.DEFAULT_PROJECTS, false, {}, { _, _ -> }) }
        }
    }
}
