package energytracker.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One server-sent event written to the browser. Empty fields are omitted so the payload matches the dashboard client. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChatEvent(String type, String delta, String tool, String message) {

    public static ChatEvent text(String delta) {
        return new ChatEvent("text", delta, null, null);
    }

    public static ChatEvent tool(String name) {
        return new ChatEvent("tool", null, name, null);
    }

    public static ChatEvent error(String message) {
        return new ChatEvent("error", null, null, message);
    }

    public static ChatEvent done() {
        return new ChatEvent("done", null, null, null);
    }
}
