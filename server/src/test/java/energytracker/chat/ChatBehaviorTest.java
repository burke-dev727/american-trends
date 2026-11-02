package energytracker.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import energytracker.energy.EnergyBehaviorTest;
import energytracker.energy.Json;
import energytracker.energy.TrendSource;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatBehaviorTest {

    private final ObjectMapper mapper = Json.mapper();

    @Test
    void agentRunsToolsAndStreamsText() throws Exception {
        FakeModel model = new FakeModel(List.of(
                List.of(functionCall("call_1", "get_eia_forecast", "{\"sources\":[\"solar\"]}"), completed("resp_1")),
                List.of(text("Solar reaches "), text("10%."), completed("resp_2"))));
        ChatAgent agent = testAgent(model);
        ChatRequest request = new ChatRequest();
        request.messages = List.of(new ChatMessage("user", "What does EIA expect for solar?"));
        request.view = new ChatView();
        request.view.period = "2026-02";
        request.view.forecast = true;

        List<ChatEvent> events = new ArrayList<>();
        agent.run(request, events::add);

        assertEquals(
                List.of(ChatEvent.tool("get_eia_forecast"), ChatEvent.text("Solar reaches "), ChatEvent.text("10%.")),
                events);
        ResponsesRequest first = model.requests.get(0);
        assertTrue(first.instructions.contains("2025-12 through 2026-01"), first.instructions);
        assertTrue(first.instructions.contains("viewing 2026-02 (an EIA forecast month)"), first.instructions);
        assertEquals(4, first.tools.size());
        assertTrue(first.previousResponseId == null || first.previousResponseId.isEmpty());

        ResponsesRequest second = model.requests.get(1);
        assertEquals("resp_1", second.previousResponseId);
        assertEquals(1, second.input.size());
        FunctionCallOutput output = assertInstanceOf(FunctionCallOutput.class, second.input.get(0));
        assertEquals("call_1", output.callId);
        assertTrue(output.output.contains("\"vintage\":\"2026-02\""), output.output);
        assertFalse(output.output.contains("\"coal\""), output.output);
    }

    @Test
    void agentStopsAfterMaxToolRounds() {
        List<List<ResponseEvent>> rounds = new ArrayList<>();
        for (int i = 0; i < ChatLimits.MAX_TOOL_ROUNDS; i++) {
            rounds.add(List.of(functionCall("c", "get_milestones", "{}"), completed("r")));
        }
        ChatRequest request = new ChatRequest();
        request.messages = List.of(new ChatMessage("user", "hi"));
        assertThrows(IOException.class, () -> testAgent(new FakeModel(rounds)).run(request, event -> {}));
    }

    @Test
    void chatRouteStreamsEvents() throws Exception {
        FakeModel model = new FakeModel(List.of(List.of(text("Hello"), completed("r"))));
        var response = mvc(testAgent(model))
                .perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        assertEquals("text/event-stream", response.getContentType());
        assertEquals(
                "data: {\"type\":\"text\",\"delta\":\"Hello\"}\n\ndata: {\"type\":\"done\"}\n\n",
                response.getContentAsString());
    }

    @Test
    void chatRouteRejectsInvalidRequests() throws Exception {
        String longMessage = "a".repeat(ChatLimits.MAX_USER_CHARS + 1);
        List<String> bodies = List.of(
                "not json",
                "{\"messages\":[]}",
                "{\"messages\":[{\"role\":\"system\",\"content\":\"x\"}]}",
                "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"},{\"role\":\"assistant\",\"content\":\"hello\"}]}",
                "{\"messages\":[{\"role\":\"user\",\"content\":\"   \"}]}",
                "{\"messages\":[{\"role\":\"user\",\"content\":\"" + longMessage + "\"}]}",
                "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"view\":{\"period\":\"2026-13\"}}");
        MockMvc mvc = mvc(testAgent(new FakeModel(List.of())));
        for (String body : bodies) {
            mvc.perform(post("/api/chat").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void chatRouteRateLimits() throws Exception {
        ChatAgent agent = testAgent(new FakeModel(List.of(List.of(completed("r")))));
        agent.limiter = new RateLimiter(1, Duration.ofMinutes(1));
        MockMvc mvc = mvc(agent);
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";
        mvc.perform(post("/api/chat").contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/chat").contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void chatDisabled() throws Exception {
        mvc(ChatAgent.disabled())
                .perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}"))
                .andExpect(status().isServiceUnavailable());
        assertFalse(statusEnabled(ChatAgent.disabled()));
        assertTrue(statusEnabled(testAgent(new FakeModel(List.of()))));
    }

    @Test
    void rateLimiterWindow() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1));
        limiter.now = now::get;
        assertTrue(limiter.allow("a"));
        assertTrue(limiter.allow("a"));
        assertFalse(limiter.allow("a"));
        assertTrue(limiter.allow("b"));
        now.set(now.get().plus(Duration.ofMinutes(1)));
        assertTrue(limiter.allow("a"));
    }

    @Test
    void clientIpTrustsOnlyTheProxyAppendedHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5:5555");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9");
        assertEquals("10.0.0.5", ChatAgent.clientIp(request, false));
        assertEquals("203.0.113.9", ChatAgent.clientIp(request, true));
    }

    @Test
    void readsServerSentEvents() throws Exception {
        String stream = "event: response.created\ndata: {\"a\":1}\n\n: comment\ndata: {\"b\":\ndata: 2}\n\n"
                + "data: [DONE]\n\ndata: {\"c\":3}";
        List<String> got = new ArrayList<>();
        ServerSentEvents.read(new java.io.BufferedReader(new StringReader(stream)), got::add);
        assertEquals(List.of("{\"a\":1}", "{\"b\":\n2}", "{\"c\":3}"), got);
    }

    @Test
    void openAiClientStreamsAndReportsErrors() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] payload = ("event: response.output_text.delta\n"
                    + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hi\"}\n\n"
                    + "event: response.completed\n"
                    + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_9\"}}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        try {
            OpenAiClient client = new OpenAiClient("sk-test", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            List<ResponseEvent> events = new ArrayList<>();
            ResponsesRequest request = new ResponsesRequest();
            request.model = "m";
            request.input = List.of(new InputMessage("user", "Hi"));
            client.stream(request, Instant.now().plusSeconds(5), events::add);
            assertEquals("Bearer sk-test", auth.get());
            assertTrue(body.get().path("stream").asBoolean());
            assertEquals("m", body.get().path("model").asText());
            assertEquals(2, events.size());
            assertEquals("Hi", events.get(0).delta);
            assertEquals("resp_9", events.get(1).response.id);
        } finally {
            server.stop(0);
        }

        HttpServer failing = HttpServer.create(new InetSocketAddress(0), 0);
        failing.createContext("/", exchange -> {
            byte[] payload = "{\"error\":{\"message\":\"Incorrect API key provided\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        failing.start();
        try {
            OpenAiClient client = new OpenAiClient("sk-test", "http://127.0.0.1:" + failing.getAddress().getPort());
            IOException error = assertThrows(IOException.class, () -> client.stream(
                    new ResponsesRequest(), Instant.now().plusSeconds(5), event -> {}));
            assertTrue(error.getMessage().contains("401"), error.getMessage());
            assertTrue(error.getMessage().contains("Incorrect API key"), error.getMessage());
        } finally {
            failing.stop(0);
        }
    }

    @Test
    void annualTotalsCombineActualAndForecast() throws Exception {
        String output = new ChatTools(EnergyBehaviorTest.sampleSource()).call("get_eia_forecast", "{\"sources\":null}");
        JsonNode totals = mapper.readTree(output).path("annualTotals");
        assertEquals(1, totals.size());
        JsonNode year = totals.get(0);
        assertEquals(2026, year.path("year").asInt());
        assertEquals(1, year.path("actualMonths").asInt());
        assertEquals(1, year.path("forecastMonths").asInt());
        assertEquals(400, year.path("totalTwh").asDouble());
        assertEquals(32, year.path("sources").path("solar").path("twh").asDouble());
        assertEquals(8, year.path("sources").path("solar").path("share").asDouble());
    }

    @Test
    void toolSchemasAreStrict() throws Exception {
        for (FunctionTool tool : new ChatTools(EnergyBehaviorTest.sampleSource()).definitions()) {
            JsonNode schema = mapper.valueToTree(tool.parameters);
            assertNotNull(schema.get("required"));
            assertEquals(schema.get("properties").size(), schema.get("required").size(), tool.name);
            assertTrue(schema.has("additionalProperties"));
            assertFalse(schema.get("additionalProperties").asBoolean(), tool.name);
        }
    }

    @Test
    void toolsReportArgumentErrors() {
        ChatTools tools = new ChatTools(EnergyBehaviorTest.sampleSource());
        assertTrue(tools.call("get_monthly_data", "{\"start\":\"2026-02\",\"end\":\"2026-01\",\"sources\":null}")
                .contains("start <= end"));
        assertTrue(tools.call("get_monthly_data", "{\"start\":\"2025-12\",\"end\":\"2026-01\",\"sources\":[\"sunshine\"]}")
                .contains("unknown source"));
        assertTrue(tools.call("get_annual_summary", "{\"start_year\":2026,\"end_year\":2020}").contains("start_year"));
        assertTrue(tools.call("get_prices", "{}").contains("unknown tool"));
        String monthly = tools.call(
                "get_monthly_data", "{\"start\":\"2025-12\",\"end\":\"2026-01\",\"sources\":[\"solar\"]}");
        assertTrue(monthly.contains("\"2026-01\""), monthly);
        assertFalse(monthly.contains("coal"), monthly);
    }

    private boolean statusEnabled(ChatAgent agent) throws Exception {
        String body = mvc(agent).perform(get("/api/chat/status")).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("enabled").asBoolean();
    }

    private MockMvc mvc(ChatAgent agent) {
        return MockMvcBuilders.standaloneSetup(new ChatController(agent, mapper))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private static ChatAgent testAgent(FakeModel model) {
        ChatAgent agent = ChatAgent.withModel(model, "test-model", "low", EnergyBehaviorTest.sampleSource(), false);
        agent.now = () -> Instant.parse("2026-02-15T00:00:00Z");
        agent.limiter = new RateLimiter(100, Duration.ofMinutes(1));
        return agent;
    }

    private static ResponseEvent text(String delta) {
        ResponseEvent event = new ResponseEvent();
        event.type = "response.output_text.delta";
        event.delta = delta;
        return event;
    }

    private static ResponseEvent functionCall(String id, String name, String arguments) {
        ResponseEvent event = new ResponseEvent();
        event.type = "response.output_item.done";
        event.item = new FunctionCall();
        event.item.type = "function_call";
        event.item.callId = id;
        event.item.name = name;
        event.item.arguments = arguments;
        return event;
    }

    private static ResponseEvent completed(String id) {
        ResponseEvent event = new ResponseEvent();
        event.type = "response.completed";
        event.response = new ResponseObject();
        event.response.id = id;
        return event;
    }

    private static final class FakeModel implements ResponseStreamer {
        private final List<List<ResponseEvent>> rounds;
        private final List<ResponsesRequest> requests = new ArrayList<>();

        private FakeModel(List<List<ResponseEvent>> rounds) {
            this.rounds = rounds;
        }

        @Override
        public void stream(ResponsesRequest request, Instant deadline, ResponseListener listener) throws IOException {
            requests.add(request);
            for (ResponseEvent event : rounds.get(requests.size() - 1)) {
                listener.onEvent(event);
            }
        }
    }
}
