package com.skb8.vivotool.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.skb8.vivotool.R
import com.skb8.vivotool.core.ImageKeys
import com.skb8.vivotool.hooks.camera.CameraFeatureConfigHook
import com.skb8.vivotool.hooks.camera.CameraZoomHook
import com.skb8.vivotool.hooks.settings.AboutPhoneRomImageHook
import com.skb8.vivotool.settings.ImageStore
import com.skb8.vivotool.ui.detail.AboutPhoneImageScreen
import com.skb8.vivotool.ui.detail.CameraFeaturesScreen
import com.skb8.vivotool.ui.detail.CameraZoomScreen

/** Экраны приложения. */
private sealed interface Route {
    data object Apps : Route
    data class AppTweaks(val app: TargetApp) : Route
    data class HookSettings(val hookId: String) : Route
    data class Crop(val uri: Uri) : Route
}

/**
 * Навигация: список приложений → твики приложения → настройки твика.
 * Стек простой, поэтому обходимся без navigation-compose.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<Route>(Route.Apps) }
    var imageVersion by remember { mutableIntStateOf(0) }

    fun push(route: Route) = stack.add(route)
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) push(Route.Crop(uri))
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val route = stack.last()) {
        Route.Apps -> AppsScreen(onOpenApp = { push(Route.AppTweaks(it)) })

        is Route.AppTweaks -> AppHooksScreen(
            app = route.app,
            onBack = ::pop,
            onOpenHook = { hook -> push(Route.HookSettings(hook.id)) }
        )

        is Route.HookSettings -> when (route.hookId) {
            AboutPhoneRomImageHook.ID -> AboutPhoneImageScreen(
                imageVersion = imageVersion,
                onBack = ::pop,
                onPickImage = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearImage = {
                    ImageStore.clear(context, ImageKeys.ABOUT_PHONE_ROM)
                    imageVersion++
                }
            )

            CameraFeatureConfigHook.ID -> CameraFeaturesScreen(onBack = ::pop)

            CameraZoomHook.ID -> CameraZoomScreen(onBack = ::pop)

            else -> LaunchedEffect(route.hookId) { pop() }
        }

        is Route.Crop -> CropScreen(
            uri = route.uri,
            targetWidth = AboutPhoneRomImageHook.TARGET_WIDTH,
            targetHeight = AboutPhoneRomImageHook.TARGET_HEIGHT,
            onCancel = ::pop,
            onCropped = { bitmap ->
                val saved = ImageStore.save(context, ImageKeys.ABOUT_PHONE_ROM, bitmap)
                pop()
                imageVersion++
                Toast.makeText(
                    context,
                    if (saved) R.string.image_saved else R.string.image_save_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}
