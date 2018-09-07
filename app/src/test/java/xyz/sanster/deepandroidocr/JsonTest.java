package xyz.sanster.deepandroidocr;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Created by cwq on 18-3-2.
 */

public class JsonTest {
    @Test
    public void test() throws JSONException {
        String jsonString = new JSONObject()
                .put("JSON1", "Hello World!")
                .put("JSON2", "Hello my World!")
                .put("JSON3", new JSONObject()
                        .put("key1", "value1")).toString();

        System.out.println(jsonString);

    }
}
