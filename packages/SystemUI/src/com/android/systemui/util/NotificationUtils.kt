/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.util

import android.app.Notification
import android.app.Notification.MessagingStyle
import android.app.Person
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification

import androidx.core.content.ContextCompat

object NotificationUtils {

    fun resolveNotificationContent(sbn: StatusBarNotification): Pair<String, String> {
        val titleText = sbn.notification.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: ""
        val contentText = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: sbn.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ""
        return titleText.toString() to contentText.toString()
    }

    fun resolveNotificationIcon(sbn: StatusBarNotification, context: Context): Drawable? {
        val extras = sbn.notification.extras
        val largeImage = extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON_BIG)
        val bigPicture = extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
        if (largeImage != null) {
            return BitmapDrawable(context.resources, largeImage)
        } else if (bigPicture != null) {
            return BitmapDrawable(context.resources, bigPicture)
        }
        val avatarIcon = getAvatarIcon(sbn)
        if (avatarIcon != null) return avatarIcon.loadDrawable(context)

        val iconObject = sequenceOf(
            extras.get(Notification.EXTRA_CONVERSATION_ICON),
            extras.get(Notification.EXTRA_LARGE_ICON),
            extras.get(Notification.EXTRA_SMALL_ICON)
        ).filterNotNull().firstOrNull()

        return when (iconObject) {
            is Bitmap -> BitmapDrawable(context.resources, iconObject)
            is Icon -> {
                val bitmap = iconObject.toBitmap(context)
                if (bitmap != null) {
                    BitmapDrawable(context.resources, bitmap)
                } else {
                    resolveAppIcon(sbn, context)
                }
            }
            is Drawable -> iconObject
            else -> resolveAppIcon(sbn, context)
        }
    }

    private fun getAvatarIcon(sbn: StatusBarNotification): Icon? {
        val extras: Bundle = sbn.notification.extras
        val messages =
            MessagingStyle.Message.getMessagesFromBundleArray(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            )
        val user = extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)
        for (i in messages.indices.reversed()) {
            val message = messages[i]
            val sender = message.senderPerson
            if (sender != null && sender !== user) {
                return sender.icon
            }
        }
        return null
    }

    fun resolveSmallIcon(sbn: StatusBarNotification, context: Context): Drawable? {
        return try {
            sbn.notification.smallIcon?.let { icon ->
                when (icon) {
                    is Icon -> {
                        icon.loadDrawable(context)
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun resolveAppIcon(sbn: StatusBarNotification, context: Context): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(getApplicationInfo(sbn, context))
        } catch (e: Exception) {
            null
        }
    }

    fun getApplicationInfo(sbn: StatusBarNotification, context: Context): ApplicationInfo {
        return context.packageManager.getApplicationInfo(sbn.packageName, 0)
    }

    private fun Icon.toBitmap(context: Context): Bitmap? {
        return when (type) {
            Icon.TYPE_BITMAP -> bitmap
            Icon.TYPE_RESOURCE -> {
                val drawable = loadDrawable(context) ?: return null
                drawable.toBitmap()
            }
            Icon.TYPE_URI -> {
                val inputStream = context.contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(inputStream)
            }
            else -> null
        }
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable) {
            return this.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            intrinsicWidth.takeIf { it > 0 } ?: 1,
            intrinsicHeight.takeIf { it > 0 } ?: 1,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}
