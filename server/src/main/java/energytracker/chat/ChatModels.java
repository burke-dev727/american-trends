package energytracker.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import energytracker.energy.Periods;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
class ChatRequest {
    public List<ChatMessage> messages;
    public ChatView view;

    void validate() {
        List<ChatMessage> items = messages == null ? List.of() : messages;
        if (items.isEmpty() || items.size() > ChatLimits.MAX_MESSAGES) {
            throw new IllegalArgumentException("messages must contain 1 to " + ChatLimits.MAX_MESSAGES + " items");
        }
        for (ChatMessage message : items) {
            int limit = ChatLimits.MAX_USER_CHARS;
            if ("assistant".equals(message.role)) {
                limit = ChatLimits.MAX_ASSISTANT_CHARS;
            } else if (!"user".equals(message.role)) {
                throw new IllegalArgumentException("message role must be \"user\" or \"assistant\"");
            }
            String content = message.content == null ? "" : message.content;
            if (content.strip().isEmpty() || content.codePointCount(0, content.length()) > limit) {
                throw new IllegalArgumentException(message.role + " messages must be 1 to " + limit + " characters");
            }
        }
        if (!"user".equals(items.get(items.size() - 1).role)) {
            throw new IllegalArgumentException("the last message must be from the user");
        }
        if (view != null && !Periods.valid(view.period)) {
            throw new IllegalArgumentException(Periods.INVALID);
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ChatMessage {
    public String role;
    public String content;

    ChatMessage() {}

    ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ChatView {
    public String period;
    public boolean forecast;
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
class ResponsesRequest {
    public String model;
    public String instructions;
    public List<Object> input;
    public List<FunctionTool> tools;

    @JsonProperty("previous_response_id")
    public String previousResponseId;

    @JsonProperty("max_output_tokens")
    public int maxOutputTokens;

    public Reasoning reasoning;
    public boolean stream;
}

class Reasoning {
    public String effort;

    Reasoning() {}

    Reasoning(String effort) {
        this.effort = effort;
    }
}

class InputMessage {
    public String role;
    public String content;

    InputMessage() {}

    InputMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}

class FunctionCallOutput {
    public String type;

    @JsonProperty("call_id")
    public String callId;

    public String output;

    FunctionCallOutput() {}

    FunctionCallOutput(String callId, String output) {
        this.type = "function_call_output";
        this.callId = callId;
        this.output = output;
    }
}

class FunctionTool {
    public String type;
    public String name;
    public String description;
    public Map<String, Object> parameters;
    public boolean strict;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ResponseEvent {
    public String type;
    public String delta;
    public FunctionCall item;
    public ResponseObject response;
    public String message;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class FunctionCall {
    public String type;

    @JsonProperty("call_id")
    public String callId;

    public String name;
    public String arguments;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ResponseObject {
    public String id;
    public ApiError error;

    @JsonProperty("incomplete_details")
    public IncompleteDetails incompleteDetails;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ApiError {
    public String message;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class IncompleteDetails {
    public String reason;
}

final class ChatLimits {
    static final int MAX_MESSAGES = 20;
    static final int MAX_USER_CHARS = 2000;
    static final int MAX_ASSISTANT_CHARS = 8000;
    static final int MAX_REQUEST_BYTES = 256 * 1024;
    static final int MAX_TOOL_ROUNDS = 6;
    static final int MAX_OUTPUT_TOKENS = 2000;
    static final int REQUESTS_PER_MINUTE = 10;

    static final String UNAVAILABLE =
            "Chat requires OPENAI_API_KEY and DATA_SOURCE=databricks or snapshot";
    static final String FAILED = "The assistant couldn't answer right now. Please try again.";
    static final String RATE_LIMITED = "Too many questions in the last minute. Please wait a moment.";

    private ChatLimits() {}
}
