package xyz.sanster.deepandroidocr.camera

import kotlin.experimental.and

/**
 * The purpose of this class hierarchy is to abstract different bitmap implementations across
 * platforms into a standard interface for requesting greyscale luminance values. The interface
 * only provides immutable methods; therefore crop and rotation create copies. This is to ensure
 * that one Reader does not modify the original luminance source and leave it in an unknown state
 * for other Readers in the chain.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
abstract class LuminanceSource protected constructor(
        /**
         * @return The width of the bitmap.
         */
        val width: Int,
        /**
         * @return The height of the bitmap.
         */
        val height: Int) {

    /**
     * Fetches luminance data for the underlying bitmap. Values should be fetched using:
     * `int luminance = array[y * width + x] & 0xff`
     *
     * @return A row-major 2D array of luminance values. Do not use result.length as it may be
     * larger than width * height bytes on some platforms. Do not modify the contents
     * of the result.
     */
    abstract val matrix: ByteArray

    /**
     * @return Whether this subclass supports cropping.
     */
    open val isCropSupported: Boolean
        get() = false

    /**
     * @return Whether this subclass supports counter-clockwise rotation.
     */
    val isRotateSupported: Boolean
        get() = false

    /**
     * Fetches one row of luminance data from the underlying platform's bitmap. Values range from
     * 0 (black) to 255 (white). Because Java does not have an unsigned byte type, callers will have
     * to bitwise and with 0xff for each value. It is preferable for implementations of this method
     * to only fetch this row rather than the whole image, since no 2D Readers may be installed and
     * getMatrix() may never be called.
     *
     * @param y The row to fetch, which must be in [0,getHeight())
     * @param row An optional preallocated array. If null or too small, it will be ignored.
     * Always use the returned object, and ignore the .length of the array.
     * @return An array containing the luminance data.
     */
    abstract fun getRow(y: Int, row: ByteArray): ByteArray

    /**
     * Returns a new object with cropped image data. Implementations may keep a reference to the
     * original data rather than a copy. Only callable if isCropSupported() is true.
     *
     * @param left The left coordinate, which must be in [0,getWidth())
     * @param top The top coordinate, which must be in [0,getHeight())
     * @param width The width of the rectangle to crop.
     * @param height The height of the rectangle to crop.
     * @return A cropped version of this object.
     */
    open fun crop(left: Int, top: Int, width: Int, height: Int): LuminanceSource {
        throw UnsupportedOperationException("This luminance source does not support cropping.")
    }

    /**
     * @return a wrapper of this `LuminanceSource` which inverts the luminances it returns -- black becomes
     * white and vice versa, and each value becomes (255-value).
     */
//    fun invert(): LuminanceSource {
//        return InvertedLuminanceSource(this)
//    }

    /**
     * Returns a new object with rotated image data by 90 degrees counterclockwise.
     * Only callable if [.isRotateSupported] is true.
     *
     * @return A rotated version of this object.
     */
    fun rotateCounterClockwise(): LuminanceSource {
        throw UnsupportedOperationException("This luminance source does not support rotation by 90 degrees.")
    }

    /**
     * Returns a new object with rotated image data by 45 degrees counterclockwise.
     * Only callable if [.isRotateSupported] is true.
     *
     * @return A rotated version of this object.
     */
    fun rotateCounterClockwise45(): LuminanceSource {
        throw UnsupportedOperationException("This luminance source does not support rotation by 45 degrees.")
    }

    override fun toString(): String {
        var row = ByteArray(width)
        val result = StringBuilder(height * (width + 1))
        for (y in 0 until height) {
            row = getRow(y, row)
            for (x in 0 until width) {
                val luminance = row[x] and 0xFF.toByte()
                val c: Char
                if (luminance < 0x40) {
                    c = '#'
                } else if (luminance < 0x80) {
                    c = '+'
                } else if (luminance < 0xC0) {
                    c = '.'
                } else {
                    c = ' '
                }
                result.append(c)
            }
            result.append('\n')
        }
        return result.toString()
    }

}


