package xyz.sanster.deepandroidocr

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

import org.junit.Assert.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    class Json() {
        val json = JSONObject()

        constructor(init: Json.() -> Unit) : this() {
            this.init()
        }

        infix fun <T> String.To(value: T) {
            json.put(this, value)
        }

        override fun toString(): String {
            return json.toString()
        }
    }

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val date = Date()
        println(date.toString())
        println(dateFormat.format(date))


        val json = JSONObject()
        val detail = JSONObject()
        detail.put("user_id", 123)
        detail.put("date", dateFormat.format(date))
        detail.put("id", 1)

        val attendArray = JSONArray()
        attendArray.put(detail)

        json.put("device_id", 11)
        json.put("attend_array", attendArray)

        println(json)

        var json2: JSONObject?
        json2 = JSONObject(mapOf(
                "device_id" to 11,
                "attend_array" to arrayOf(
                        JSONObject(mapOf(
                                "user_id" to 123,
                                "date" to dateFormat.format(date),
                                "id" to 1
                        ))
                )
        ))

        print(json2.toString())
    }

    class Msg(val msg: String, val code: Int)

    @Test
    fun test_parse_json() {
//        val jsonStr = "{\"msg\": \" Posted content type isn't multipart/form-data\", \"code\": 500 }"
//        val json = JSONObject(jsonStr)
//        println(json.optString("msg"))
//        println(json.getInt("code"))
//
//        val gson = Gson()
//
//        val msg = gson.fromJson(jsonStr, Msg::class.java)
//        println(msg.msg)
//        println(msg.code)
    }

    @Test
    fun tt() {
        (0.. 5).forEach { i ->
            println(i)
        }
    }
}
