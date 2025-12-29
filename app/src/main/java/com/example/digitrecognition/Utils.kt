import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

fun decodeBitmapApplyingExif(file: File): Bitmap {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        ?: error("Cannot decode bitmap from ${file.absolutePath}")

    val exif = ExifInterface(file)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)

        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)

        // Рідкісні випадки (можуть траплятись на деяких девайсах)
        ExifInterface.ORIENTATION_TRANSPOSE -> { // flip + rotate 270
            matrix.preScale(-1f, 1f)
            matrix.postRotate(270f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> { // flip + rotate 90
            matrix.preScale(-1f, 1f)
            matrix.postRotate(90f)
        }

        else -> return bitmap
    }

    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}
