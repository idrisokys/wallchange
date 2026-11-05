package com.idris.duvarkagidi

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import kotlin.concurrent.thread

/** Arayüzsüz: Paylaş menüsünden gelen görseli ana ekran + kilit ekranı duvar kağıdı yapar. */
class SetWallpaperActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = extractUri(intent)
        if (uri == null) {
            Toast.makeText(this, R.string.no_image, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Toast.makeText(this, R.string.working, Toast.LENGTH_SHORT).show()
        val app = applicationContext
        thread {
            val msg = try {
                if (applyWallpaper(app, uri)) R.string.done else R.string.done_home_only
            } catch (e: UnsupportedOperationException) {
                R.string.not_supported
            } catch (e: Exception) {
                R.string.failed
            }
            runOnUiThread {
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun extractUri(i: Intent?): Uri? {
        if (i == null) return null
        i.data?.let { return it }
        val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return stream ?: i.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    /** true: ana + kilit, false: sadece ana ekran (kilit ekranı desteklenmiyor, ör. Android TV) */
    private fun applyWallpaper(ctx: Context, uri: Uri): Boolean {
        val wm = WallpaperManager.getInstance(ctx)
        if (!wm.isWallpaperSupported || !wm.isSetWallpaperAllowed) {
            throw UnsupportedOperationException()
        }
        val bmp = fitToScreen(decode(ctx, uri), wm)
        return try {
            wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            true
        } catch (e: Exception) {
            // TV gibi kilit ekranı olmayan cihazlar
            wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
            false
        }
    }

    private fun decode(ctx: Context, uri: Uri): Bitmap {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // setBitmap için gerekli
            val max = 4096
            val w = info.size.width
            val h = info.size.height
            val scale = max.toFloat() / maxOf(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        }
    }

    /** Görseli ekran oranına göre ortadan kırpar. */
    private fun fitToScreen(src: Bitmap, wm: WallpaperManager): Bitmap {
        val tw = wm.desiredMinimumWidth
        val th = wm.desiredMinimumHeight
        if (tw <= 0 || th <= 0) return src
        val target = tw.toFloat() / th
        val cur = src.width.toFloat() / src.height
        return when {
            cur > target -> {
                val w = (src.height * target).toInt()
                Bitmap.createBitmap(src, (src.width - w) / 2, 0, w, src.height)
            }
            cur < target -> {
                val h = (src.width / target).toInt()
                Bitmap.createBitmap(src, 0, (src.height - h) / 2, src.width, h)
            }
            else -> src
        }
    }
}
