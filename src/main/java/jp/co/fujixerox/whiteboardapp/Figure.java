/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujixerox.whiteboardapp;

import java.io.StringWriter;
import javax.json.Json;
import javax.json.JsonObject;

/**
 *
 * @author haiyang
 */
public class Figure {
    private JsonObject json;

    public JsonObject getJson() {
        return json;
    }

    public void setJson(JsonObject json) {
        this.json = json;
    }

    public Figure(JsonObject json) {
        this.json = json;
    }

    @Override
    public String toString() {
        StringWriter writer=new StringWriter();
        Json.createWriter(writer).write(json);
        return writer.toString();
    }
    
}
