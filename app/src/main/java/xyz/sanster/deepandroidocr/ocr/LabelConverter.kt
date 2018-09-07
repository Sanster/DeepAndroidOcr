package xyz.sanster.deepandroidocr.ocr

import android.content.Context
import android.util.Log
import xyz.sanster.deepandroidocr.Util

class LabelConverter(context: Context, charsFile: String) {
    companion object {
        private val TAG = LabelConverter::class.java.simpleName
    }

    private val INVALID_INDEX = -1
    private var chars: List<Char>? = null
    private val encodeMap: MutableMap<Char, Int> = mutableMapOf()
    private val decodeMap: MutableMap<Int, Char> = mutableMapOf()

    init {
        chars = Util.readChars(context, charsFile)
        Log.d(TAG, "Chars num: ${chars?.size}")
        chars?.forEachIndexed { index, char ->
            encodeMap[char] = index
            decodeMap[index] = char
        }
    }

    fun encode(label: String): List<Int> {
        return label.map { encodeMap.getOrDefault(it, INVALID_INDEX) }
    }

    fun decode(predicts: List<Int>): String {
        var res = ""
        predicts.filter { it != INVALID_INDEX }
                .forEach { res += decodeMap[it] }
        return res
    }
}