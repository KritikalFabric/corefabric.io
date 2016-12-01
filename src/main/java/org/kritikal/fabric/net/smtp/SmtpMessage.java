package org.kritikal.fabric.net.smtp;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ben on 5/14/14.
 */
public class SmtpMessage {
    public SmtpMessage() { to = new ArrayList<String>(); additional = new JsonObject(); }
    public SmtpMessage(JsonObject o)
    {
        to = new ArrayList<String>();
        from = o.getString("from");
        setBodyString(o.getString("body"));
        JsonArray ary = o.getJsonArray("to");
        for (int i = 0; i < ary.size(); ++i) to.add(ary.getString(i));
        additional = new JsonObject();
        for (String fieldName : o.fieldNames()) {
            if ("from".equals(fieldName) || "to".equals(fieldName) || "body".equals(fieldName)) continue;
            additional.put(fieldName, o.getValue(fieldName));
        }
    }
    public String from;
    private Buffer bodyBuffer;
    private String bodyString;
    public List<String> to;
    private JsonObject additional; // application additional data to pass through to the SmtpcProtocolController specialisation
    // For smtpc:
    public boolean failure = false; // only true when a fatal error occurs prior to close
    public boolean submitted = false; // only true when smtpc has submitted the message
    public void toJsonObject(JsonObject o)
    {
        JsonArray to = new JsonArray();
        for (int i = 0; i < this.to.size(); ++i)
            to.add(this.to.get(i));
        o.put("to", to);
        o.put("from", from);
        o.put("body", getBodyString());
        o.mergeIn(additional);
    }

    public JsonObject additional() { return additional; }

    public void setBuffer(Buffer buffer)
    {
        bodyString = null;
        bodyBuffer = buffer;
    }

    public String getBodyString()
    {
        if (bodyString != null)
        {
            return bodyString;
        }
        if (bodyBuffer != null)
        {
            bodyString = bodyBuffer.toString(Constants.ASCII);
            bodyBuffer = null;
        }
        return bodyString;
    }

    public Buffer getBodyBuffer()
    {
        if (bodyString != null)
        {
            bodyBuffer = Buffer.buffer(bodyString, Constants.ASCII);
            bodyString = null;
        }
        return bodyBuffer;
    }

    public void setBodyString(String value)
    {
        bodyBuffer = null;
        bodyString = value;
    }
}
