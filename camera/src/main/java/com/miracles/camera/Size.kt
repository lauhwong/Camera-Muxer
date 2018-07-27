package com.miracles.camera

/**
 * Immutable class for describing width and height dimensions in pixels.
 *
 * Create a new immutable Size instance.
 *
 * @param width  The width of the size, in pixels
 * @param height The height of the size, in pixels
 */
class Size(val width: Int, val height: Int) : Comparable<Size> {

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (this === other) {
            return true
        }
        if (other is Size) {
            val size = other as Size?
            return width == size!!.width && height == size.height
        }
        return false
    }

    override fun toString(): String {
        return width.toString() + "x" + height
    }

    override fun hashCode(): Int {
        // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        return height xor (width shl Integer.SIZE / 2 or width.ushr(Integer.SIZE / 2))
    }

    override fun compareTo(other: Size): Int {
        return width * height - other.width * other.height
    }

}
