package com.skb8.vivotool.hooks.share

import android.content.Context
import android.os.Parcel
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.XLog
import java.util.Collections
import java.util.WeakHashMap

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
 * 4. Заглушает показ Toast-уведомления об автовыключении через 10 минут при включении из шторки.
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
    private const val TOAST_CLASS = "android.widget.Toast"

    private val suppressedToasts = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )

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

        // 5. Заглушаем показ Toast при включении Vivo Share
        hookToast()

        if (hookedAny) {
            XLog.i("[$id] Хук против автовыключения Vivo Share успешно применён")
        } else {
            XLog.w("[$id] Не удалось найти целевые классы для хука Vivo Share (возможно другой процесс или версия)")
        }
    }

    private fun hookToast() {
        val toastClass = findClassOrNull(TOAST_CLASS) ?: return

        toastClass.hookAfter(
            "makeText",
            Context::class.java,
            CharSequence::class.java,
            Int::class.javaPrimitiveType
        ) { param ->
            val text = param.args[1] as? CharSequence ?: return@hookAfter
            val context = param.args[0] as? Context
            val toast = param.result ?: return@hookAfter
            if (isTimeoutToast(context, text)) {
                suppressedToasts.add(toast)
                XLog.i("[$id] Тост об автовыключении перехвачен для заглушения: \"$text\"")
            }
        }

        toastClass.hookAfter(
            "makeText",
            Context::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ) { param ->
            val resId = param.args[1] as? Int ?: return@hookAfter
            val context = param.args[0] as? Context
            val toast = param.result ?: return@hookAfter
            if (context != null) {
                val targetId = try {
                    context.resources.getIdentifier("vivoshare_toast_turn_off_vivoshare", "string", "com.vivo.share")
                } catch (_: Throwable) {
                    0
                }
                if (targetId != 0 && resId == targetId) {
                    suppressedToasts.add(toast)
                    XLog.i("[$id] Тост об автовыключении (по resId=$resId) перехвачен для заглушения")
                }
            }
        }

        toastClass.hookBefore("show") { param ->
            val toast = param.thisObject ?: return@hookBefore
            if (suppressedToasts.contains(toast)) {
                param.result = null
                XLog.i("[$id] Показ тоста об автовыключении успешно заглушен")
            }
        }
    }

    private fun isTimeoutToast(context: Context?, text: CharSequence): Boolean {
        val str = text.toString()

        if (context != null) {
            try {
                val res = context.resources
                val targetId = res.getIdentifier("vivoshare_toast_turn_off_vivoshare", "string", "com.vivo.share")
                if (targetId != 0) {
                    val appNameId = res.getIdentifier("vivoshare_app_name", "string", "com.vivo.share")
                    val appName = if (appNameId != 0) res.getString(appNameId) else ""
                    val fullExpected = try {
                        res.getString(targetId, appName)
                    } catch (_: Throwable) {
                        null
                    }
                    if (fullExpected != null && (str == fullExpected || str.contains(fullExpected))) {
                        return true
                    }

                    val rawTemplate = res.getString(targetId)
                    val cleaned = rawTemplate.replace("%1\$s", "").replace("%s", "").trim()
                    if (cleaned.isNotEmpty() && str.contains(cleaned)) {
                        return true
                    }
                }
            } catch (t: Throwable) {
                XLog.d("[$id] Ошибка при проверке строкового ресурса: ${t.message}")
            }
        }

        if (str.contains("10")) {
            val lower = str.lowercase()
            if (lower.contains("минут") || lower.contains("minute") || lower.contains("分") || lower.contains("minuto")) {
                return true
            }
        }

        return false
    }
}
