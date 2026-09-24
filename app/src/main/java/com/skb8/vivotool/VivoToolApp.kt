package com.skb8.vivotool

import android.app.Application
import com.skb8.vivotool.core.ServiceBridge

class VivoToolApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceBridge.init(this)
    }
}
