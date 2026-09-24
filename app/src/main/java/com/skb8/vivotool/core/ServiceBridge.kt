package com.skb8.vivotool.core

import android.content.Context
import android.content.SharedPreferences
import com.skb8.vivotool.settings.AppSettings
import com.skb8.vivotool.settings.ImageStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * Мост между приложением и сервисом Xposed (Vector / LibXposed).
 *
 * Через [XposedServiceHelper] приложение получает инстанс [XposedService],
 * позволяющий синхронизировать настройки в RemotePreferences и проверять статус работы фреймворка.
 */
object ServiceBridge {

    @Volatile
    var xposedService: XposedService? = null
        private set

    @Volatile
    var remotePrefs: SharedPreferences? = null
        private set

    @Volatile
    var remoteImagePrefs: SharedPreferences? = null
        private set

    val isConnected: Boolean
        get() = xposedService != null

    val frameworkName: String?
        get() = xposedService?.frameworkName ?: if (isConnected) "Vector" else null

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
                XLog.i("XposedService подключен: API ${service.apiVersion}")
                try {
                    val p = service.getRemotePreferences(Constants.PREFS_NAME)
                    remotePrefs = p
                    val imgP = service.getRemotePreferences(Constants.IMAGE_PREFS_NAME)
                    remoteImagePrefs = imgP

                    AppSettings(context).syncToRemote(p)
                    ImageStore.syncToRemote(context, imgP)
                } catch (t: Throwable) {
                    XLog.e("Ошибка при получении RemotePreferences из сервиса", t)
                }
            }

            override fun onServiceDied(service: XposedService) {
                if (xposedService == service) {
                    xposedService = null
                    remotePrefs = null
                    remoteImagePrefs = null
                    XLog.i("XposedService отключен")
                }
            }
        })
    }
}
