package com.example.simplevpn

import android.app.Application

class SimpleVpnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.applySaved(this)
    }
}
