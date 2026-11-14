package dev.moonbag.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Photos picked from the gallery for coins you create, stored as small square PNGs in app storage. */
object CoinImages {

    private const val DIR = "coin_images"
    private const val SIZE = 256

    private val cache = LruCache<String, ImageBitmap>(32)

    private fun dir(context: Context): File = File(context.filesDir, DIR).also { it.mkdirs() }

    /** Copies, crops to a square and shrinks the picture at [uri]. Blocking: call off the main thread. */
    fun importPhoto(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val source = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)

        val name = "img_" + System.currentTimeMillis() + ".png"
        File(dir(context), name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        name
    }.getOrNull()

    fun load(context: Context, name: String): ImageBitmap? {
        cache.get(name)?.let { return it }
        val file = File(dir(context), name)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.path)?.asImageBitmap() ?: return null
        cache.put(name, bitmap)
        return bitmap
    }

    fun delete(context: Context, name: String) {
        cache.remove(name)
        File(dir(context), name).delete()
    }

    fun clear(context: Context) {
        cache.evictAll()
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
