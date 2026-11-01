package org.brianwebb.hamsprint

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.brianwebb.hamsprint.data.AidLoader
import org.brianwebb.hamsprint.data.PoolLoader
import org.brianwebb.hamsprint.data.ProgressStore
import org.brianwebb.hamsprint.data.SettingsStore
import org.brianwebb.hamsprint.domain.Clock
import kotlin.random.Random

/** Manual dependency graph: the app is small enough that a DI framework would only add weight. */
class AppContainer(
    context: Context,
    val clock: Clock = Clock { System.currentTimeMillis() },
    val random: Random = Random.Default,
    progressDbName: String? = "hamsprint.db",
) {
    val pools = PoolLoader { path -> context.assets.open(path) }
    val aids = AidLoader { path -> context.assets.open(path) }
    val progress = ProgressStore(context, Dispatchers.IO, progressDbName)
    /** Outlives every screen: progress writes launched here finish even if the user navigates away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context.getSharedPreferences("settings", Context.MODE_PRIVATE))
}
