package org.brianwebb.hamsprint

import android.app.Application

class HamSprintApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
