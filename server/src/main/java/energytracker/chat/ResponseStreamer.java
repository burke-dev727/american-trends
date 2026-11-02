package energytracker.chat;

import java.io.IOException;
import java.time.Instant;

interface ResponseStreamer {
    void stream(ResponsesRequest request, Instant deadline, ResponseListener listener) throws IOException;
}

@FunctionalInterface
interface ResponseListener {
    void onEvent(ResponseEvent event) throws IOException;
}
