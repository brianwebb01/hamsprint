package org.brianwebb.hamsprint.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import java.util.concurrent.atomic.AtomicLong

sealed interface Screen {
    data object Home : Screen
    /** Adaptive training over the whole pool, or one subelement when [subelementId] is set. */
    data class Training(val subelementId: String? = null) : Screen
    data object Exam : Screen
    /** Practice-exam history and confirmation of readiness. */
    data object ExamHistory : Screen
    data object Settings : Screen
}

/** A back-stack entry owns the ViewModels of its screen; they are cleared when it is popped. */
class NavEntry(val screen: Screen) : ViewModelStoreOwner {
    val id: Long = ids.incrementAndGet()
    override val viewModelStore = ViewModelStore()

    private companion object {
        val ids = AtomicLong()
    }
}

/** Survives configuration changes; holds the back stack. */
class Navigator : ViewModel() {
    val stack: SnapshotStateList<NavEntry> = mutableStateListOf(NavEntry(Screen.Home))

    val current: NavEntry get() = stack.last()

    fun push(screen: Screen) {
        stack.add(NavEntry(screen))
    }

    /** Returns false when already at the root, so the system can handle back. */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex).viewModelStore.clear()
        return true
    }

    /** Replaces the top entry, e.g. "train again" from a finished round. */
    fun replace(screen: Screen) {
        stack.removeAt(stack.lastIndex).viewModelStore.clear()
        stack.add(NavEntry(screen))
    }

    override fun onCleared() {
        stack.forEach { it.viewModelStore.clear() }
    }
}
