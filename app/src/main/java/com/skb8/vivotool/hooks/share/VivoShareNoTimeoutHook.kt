package com.skb8.vivotool.hooks.share

import android.os.Parcel
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.XLog

/**
 * Отключение автоматического выключения Vivo Share через 10 минут.
 *
 * В прошивке OriginOS при включении Vivo Share запускается таймер на 10 минут:
 * `ShareServiceProxy` взводит `Timer` (600 000 мс) и системный `AlarmManager` (605 000 мс),
 * после чего вызывает `IVivoShareInnerService.closeVivoShareAfterTenMinutes()`.
 * Реализация в `VivoShareServicePool` гасит сетевые интерфейсы (Wi-Fi Direct, BLE)
 * и закрывает службу.
 *
 * Данный хук:
 * 1. Блокирует метод `closeVivoShareAfterTenMinutes()` во всех реализациях `IVivoShareInnerService`
 *    внутри `VivoShareServicePool` (заменяя его пустым телом).
 * 2. Перехватывает Binder IPC транзакцию `TRANSACTION_closeVivoShareAfterTenMinutes`
 *    в `IVivoShareInnerService.Stub.onTransact`, предотвращая вызов из внешних процессов (`:proxy`).
 * 3. Блокирует клиентский вызов `closeVivoShareAfterTenMinutes()` в `IVivoShareInnerService.Stub.Proxy`,
 *    чтобы процесс `:proxy` даже не посылал IPC-запрос при срабатывании таймера/аларма.
 */
object VivoShareNoTimeoutHook : BaseHook() {

    const val ID = "vivo_share_no_timeout"

    override val id: String = ID
    override val titleRes: Int = R.string.hook_vivo_share_no_timeout_title
    override val descriptionRes: Int = R.string.hook_vivo_share_no_timeout_description

    override val targetPackages: Set<String> = setOf(Constants.VIVO_SHARE_PACKAGE)

    override val enabledByDefault: Boolean = true

    private const val INNER_SERVICE_CLASS = "com.vivo.share.service.inner.IVivoShareInnerService"
    private const val INNER_SERVICE_STUB_CLASS = "com.vivo.share.service.inner.IVivoShareInnerService\$Stub"
    private const val INNER_SERVICE_PROXY_CLASS = "com.vivo.share.service.inner.IVivoShareInnerService\$Stub\$Proxy"
    private const val SERVICE_POOL_CLASS = "com.vivo.share.services.VivoShareServicePool"

    override fun onHook() {
        var hookedAny = false

        val stubClass = findClassOrNull(INNER_SERVICE_STUB_CLASS)
        val innerServiceClass = findClassOrNull(INNER_SERVICE_CLASS)
        val poolClass = findClassOrNull(SERVICE_POOL_CLASS)
        val proxyClass = findClassOrNull(INNER_SERVICE_PROXY_CLASS)

        // 1. Блокируем вызов в Proxy (клиентская сторона в процессе :proxy)
        if (proxyClass != null) {
            val unhookProxy = proxyClass.replace("closeVivoShareAfterTenMinutes") {
                XLog.i("[$id] Заблокирован вызов Proxy.closeVivoShareAfterTenMinutes()")
                null
            }
            if (unhookProxy != null) {
                hookedAny = true
                XLog.i("[$id] Установлен перехват на $INNER_SERVICE_PROXY_CLASS.closeVivoShareAfterTenMinutes")
            }
        }

        // 2. Блокируем IPC вызов на уровне Binder.onTransact в Stub (серверная сторона)
        if (stubClass != null) {
            val txCode = try {
                val field = stubClass.getDeclaredField("TRANSACTION_closeVivoShareAfterTenMinutes")
                field.isAccessible = true
                field.getInt(null)
            } catch (_: Throwable) {
                4 // Стандартный AIDL index транзакции closeVivoShareAfterTenMinutes
            }

            val unhookTransact = stubClass.hookBefore(
                "onTransact",
                Int::class.javaPrimitiveType,
                Parcel::class.java,
                Parcel::class.java,
                Int::class.javaPrimitiveType
            ) { param ->
                val code = param.args[0] as? Int ?: return@hookBefore
                if (code == txCode) {
                    val reply = param.args[2] as? Parcel
                    reply?.writeNoException()
                    param.result = true
                    XLog.i("[$id] Заблокирован IPC вызов onTransact(closeVivoShareAfterTenMinutes)")
                }
            }
            if (unhookTransact != null) {
                hookedAny = true
                XLog.i("[$id] Установлен перехват onTransact на $INNER_SERVICE_STUB_CLASS (code=$txCode)")
            }

            // 3. Перехватываем конструктор любых реализаций Stub, чтобы захукать сам метод
            stubClass.hookAllConstructorsAfter { param ->
                val instance = param.thisObject ?: return@hookAllConstructorsAfter
                val implClass = instance.javaClass
                implClass.replace("closeVivoShareAfterTenMinutes") {
                    XLog.i("[$id] Заблокирован вызов closeVivoShareAfterTenMinutes() в ${implClass.name}")
                    null
                }
            }
        }

        // 4. Также ищем уже существующие внутренние классы в VivoShareServicePool
        if (poolClass != null) {
            try {
                // Если метод объявлен прямо в пуле сервисов
                poolClass.replace("closeVivoShareAfterTenMinutes") {
                    XLog.i("[$id] Заблокирован вызов closeVivoShareAfterTenMinutes() в VivoShareServicePool")
                    null
                }

                poolClass.declaredClasses.forEach { inner ->
                    val isServiceInner = (innerServiceClass != null && innerServiceClass.isAssignableFrom(inner)) ||
                        (stubClass != null && stubClass.isAssignableFrom(inner))
                    if (isServiceInner) {
                        val unhook = inner.replace("closeVivoShareAfterTenMinutes") {
                            XLog.i("[$id] Заблокирован вызов closeVivoShareAfterTenMinutes() в ${inner.name}")
                            null
                        }
                        if (unhook != null) {
                            hookedAny = true
                            XLog.i("[$id] Установлен перехват closeVivoShareAfterTenMinutes на ${inner.name}")
                        }
                    }
                }
            } catch (t: Throwable) {
                XLog.e("[$id] Ошибка при поиске классов в VivoShareServicePool", t)
            }
        }

        if (hookedAny) {
            XLog.i("[$id] Хук против автовыключения Vivo Share успешно применён")
        } else {
            XLog.w("[$id] Не удалось найти целевые классы для хука Vivo Share (возможно другой процесс или версия)")
        }
    }
}
