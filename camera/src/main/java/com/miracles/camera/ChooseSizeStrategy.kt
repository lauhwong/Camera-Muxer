package com.miracles.camera

import android.os.Parcel
import android.os.Parcelable
import android.view.Surface
import kotlin.math.sign

/**
 * Created by lxw
 */
interface ChooseSizeStrategy : Parcelable {

    fun chooseSize(preview: CameraPreview, displayOrientation: Int, cameraSensorOrientation: Int, facing: Int, sizes: List<Size>): Size

    fun bytesPoolSize(size: Size) = 8

    open class LargestSizeStrategy() : ChooseSizeStrategy {
        constructor(parcel: Parcel) : this() {

        }

        override fun chooseSize(preview: CameraPreview, displayOrientation: Int, cameraSensorOrientation: Int, facing: Int, sizes: List<Size>): Size {
            return sizes.sortedDescending().first()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LargestSizeStrategy) return false
            return true
        }

        override fun hashCode(): Int {
            return javaClass.hashCode()
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {

        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<LargestSizeStrategy> {
            override fun createFromParcel(parcel: Parcel): LargestSizeStrategy {
                return LargestSizeStrategy(parcel)
            }

            override fun newArray(size: Int): Array<LargestSizeStrategy?> {
                return arrayOfNulls(size)
            }
        }

    }

    open class AspectRatioStrategy(val aspectRatio: Float, val maxHeight: Int, val maxWidth: Int) : ChooseSizeStrategy {

        constructor(parcel: Parcel) : this(
                parcel.readFloat(),
                parcel.readInt(),
                parcel.readInt()) {
        }

        override fun chooseSize(preview: CameraPreview, displayOrientation: Int, cameraSensorOrientation: Int, facing: Int, sizes: List<Size>): Size {
            if (!preview.isReady()) return sizes.first()
            val sorted = sizes.sortedWith(Comparator { o1, o2 ->
                (Math.abs(aspectRatio - o1.width.toFloat() / o1.height)
                        - Math.abs(aspectRatio - o2.width.toFloat() / o2.height)).sign.toInt()
            })
            var swappedDimensions = false
            when (displayOrientation) {
                Surface.ROTATION_0, Surface.ROTATION_180 -> if (cameraSensorOrientation == 90 || cameraSensorOrientation == 270) {
                    swappedDimensions = true
                }
                Surface.ROTATION_90, Surface.ROTATION_270 -> if (cameraSensorOrientation == 0 || cameraSensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            var desiredWidth = preview.getWidth()
            var desiredHeight = preview.getHeight()
            var maxSizeWidth = maxWidth
            var maxSizeHeight = maxHeight
            if (swappedDimensions) {
                maxSizeWidth = maxHeight
                maxSizeHeight = maxWidth
                desiredWidth = preview.getHeight()
                desiredHeight = preview.getWidth()
            }
            var lastDelta = Int.MAX_VALUE
            var lastIndex = 0
            for ((index, size) in sorted.withIndex()) {
                if (size.width > maxSizeWidth || size.height > maxSizeHeight) continue
                val delta = Math.abs(size.width - desiredWidth) + Math.abs(size.height - desiredHeight)
                if (delta <= lastDelta) {
                    lastDelta = delta
                    lastIndex = index
                }
            }
            return sorted[lastIndex]
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeFloat(aspectRatio)
            parcel.writeInt(maxHeight)
            parcel.writeInt(maxWidth)
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AspectRatioStrategy) return false

            if (aspectRatio != other.aspectRatio) return false
            if (maxHeight != other.maxHeight) return false
            if (maxWidth != other.maxWidth) return false

            return true
        }

        override fun hashCode(): Int {
            var result = aspectRatio.hashCode()
            result = 31 * result + maxHeight
            result = 31 * result + maxWidth
            return result
        }

        companion object CREATOR : Parcelable.Creator<AspectRatioStrategy> {
            override fun createFromParcel(parcel: Parcel): AspectRatioStrategy {
                return AspectRatioStrategy(parcel)
            }

            override fun newArray(size: Int): Array<AspectRatioStrategy?> {
                return arrayOfNulls(size)
            }
        }

    }
}