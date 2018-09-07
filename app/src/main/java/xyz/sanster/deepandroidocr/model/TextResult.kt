package xyz.sanster.deepandroidocr.model

import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable

class TextResult() : Parcelable {
    lateinit var location: Rect
    var words: String = ""
    var score: Float = 0.0f

    constructor(location: Rect, words: String, score: Float) : this() {
        this.location = location
        this.words = words
        this.score = score
    }

    constructor(parcel: Parcel) : this() {
        this.words = parcel.readString()
        this.location = parcel.readParcelable(Rect::class.java.classLoader)
        this.score = parcel.readFloat()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this.words)
        parcel.writeParcelable(this.location, flags)
        parcel.writeFloat(this.score)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TextResult> {
        override fun createFromParcel(parcel: Parcel): TextResult {
            return TextResult(parcel)
        }

        override fun newArray(size: Int): Array<TextResult?> {
            return arrayOfNulls(size)
        }
    }
}