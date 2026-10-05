package com.brandmauer.abplayer

import android.app.Application

class ABPlayerApp : Application() {
    lateinit var hub: Hub
        private set

    override fun onCreate() {
        super.onCreate()
        hub = Hub(this)
    }
}
