package com.skb8.vivotool.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.skb8.vivotool.core.ImageKeys
import com.skb8.vivotool.hooks.settings.AboutPhoneRomImageHook
import com.skb8.vivotool.settings.ImageStore

/** Корневой composable: главный экран и экран обрезки картинки. */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var imageVersion by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) cropUri = uri
    }

    val uri = cropUri
    if (uri != null) {
        CropScreen(
            uri = uri,
            targetWidth = AboutPhoneRomImageHook.TARGET_WIDTH,
            targetHeight = AboutPhoneRomImageHook.TARGET_HEIGHT,
            onCancel = { cropUri = null },
            onCropped = { bitmap ->
                val saved = ImageStore.save(context, ImageKeys.ABOUT_PHONE_ROM, bitmap)
                cropUri = null
                imageVersion++
                Toast.makeText(
                    context,
                    if (saved) {
                        "Готово. Закройте «Настройки» (force stop), чтобы увидеть картинку"
                    } else {
                        "Не удалось сохранить картинку"
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    } else {
        MainScreen(
            imageVersion = imageVersion,
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
    }
}
