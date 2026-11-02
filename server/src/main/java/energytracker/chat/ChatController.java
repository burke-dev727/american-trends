package energytracker.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!export")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatAgent agent;
    private final ObjectMapper mapper;

    public ChatController(ChatAgent agent, ObjectMapper mapper) {
        this.agent = agent;
        this.mapper = mapper;
    }

    @GetMapping("/api/chat/status")
    public ResponseEntity<java.util.Map<String, Boolean>> status() {
        return ResponseEntity.ok(java.util.Map.of("enabled", agent.enabled()));
    }

    @PostMapping("/api/chat")
    public void chat(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!agent.enabled()) {
            writeText(response, 503, ChatLimits.UNAVAILABLE);
            return;
        }
        if (!agent.allow(request)) {
            writeText(response, 429, ChatLimits.RATE_LIMITED);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(ChatLimits.MAX_REQUEST_BYTES + 1);
        if (body.length > ChatLimits.MAX_REQUEST_BYTES) {
            writeText(response, 400, "Request body must be JSON");
            return;
        }
        ChatRequest chatRequest;
        try {
            chatRequest = mapper.readValue(body, ChatRequest.class);
            chatRequest.validate();
        } catch (IllegalArgumentException ex) {
            writeText(response, 400, ex.getMessage());
            return;
        } catch (IOException ex) {
            writeText(response, 400, "Request body must be JSON");
            return;
        }

        response.setStatus(200);
        response.setContentType("text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        try {
            agent.run(chatRequest, event -> writeEvent(response, event));
            writeEvent(response, ChatEvent.done());
        } catch (ClientGone ex) {
            // The browser closed the stream.
        } catch (Exception ex) {
            log.warn("chat failed: {}", ex.toString());
            try {
                writeEvent(response, ChatEvent.error(ChatLimits.FAILED));
            } catch (ClientGone ignored) {
                // The browser closed the stream.
            }
        }
    }

    private void writeEvent(HttpServletResponse response, ChatEvent event) {
        try {
            byte[] json = mapper.writeValueAsBytes(event);
            var out = response.getOutputStream();
            out.write("data: ".getBytes(StandardCharsets.UTF_8));
            out.write(json);
            out.write(new byte[] {'\n', '\n'});
            out.flush();
            response.flushBuffer();
        } catch (IOException ex) {
            throw new ClientGone(ex);
        }
    }

    private static void writeText(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getOutputStream().write((message + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static final class ClientGone extends RuntimeException {
        private ClientGone(IOException cause) {
            super(cause);
        }
    }
}
