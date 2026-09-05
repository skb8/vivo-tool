package com.skb8.vivotool.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.skb8.vivotool.core.ImageKeys
import com.skb8.vivotool.hooks.settings.AboutPhoneRomImageHook
import com.skb8.vivotool.settings.ImageStore

/** Управление картинкой для хука [AboutPhoneRomImageHook]. */
@Composable
fun AboutPhoneImageCard(
    imageVersion: Int,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit
) {
    val context = LocalContext.current
    val preview = remember(imageVersion) {
        ImageStore.load(context, ImageKeys.ABOUT_PHONE_ROM)
    }
    val sizeKb = remember(imageVersion) {
        ImageStore.sizeBytes(context, ImageKeys.ABOUT_PHONE_ROM) / 1024
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Image, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text(
                    text = "  Изображение",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(Modifier.height(12.dp))

            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            AboutPhoneRomImageHook.TARGET_WIDTH.toFloat() /
                                AboutPhoneRomImageHook.TARGET_HEIGHT.toFloat()
                        )
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${AboutPhoneRomImageHook.TARGET_WIDTH}×" +
                        "${AboutPhoneRomImageHook.TARGET_HEIGHT}, ~$sizeKb КБ. " +
                        "Чтобы увидеть результат, закройте «Настройки» (force stop) и откройте заново.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            AboutPhoneRomImageHook.TARGET_WIDTH.toFloat() /
                                AboutPhoneRomImageHook.TARGET_HEIGHT.toFloat()
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.06f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Картинка не выбрана —\nиспользуется стандартная",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Выбранное изображение будет обрезано под " +
                        "${AboutPhoneRomImageHook.TARGET_WIDTH}×" +
                        "${AboutPhoneRomImageHook.TARGET_HEIGHT} — размер оригинального ресурса " +
                        AboutPhoneRomImageHook.RESOURCE_NAME + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickImage) {
                    Text(if (preview != null) "Заменить" else "Выбрать из галереи")
                }
                if (preview != null) {
                    TextButton(onClick = onClearImage) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Text("  Сбросить")
                    }
                }
            }
        }
    }
}
