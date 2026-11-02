package energytracker.chat;

import java.io.BufferedReader;
import java.io.IOException;

/** Reads the {@code data} field of each event in a {@code text/event-stream} body. */
final class ServerSentEvents {

    private ServerSentEvents() {}

    @FunctionalInterface
    interface Handler {
        void onData(String data) throws IOException;
    }

    static void read(BufferedReader reader, Handler handler) throws IOException {
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                dispatch(data, handler);
                continue;
            }
            if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                String value = line.substring("data:".length());
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                data.append(value);
            }
        }
        dispatch(data, handler);
    }

    private static void dispatch(StringBuilder data, Handler handler) throws IOException {
        if (data.length() == 0) {
            return;
        }
        String text = data.toString();
        data.setLength(0);
        if (!"[DONE]".equals(text)) {
            handler.onData(text);
        }
    }
}
