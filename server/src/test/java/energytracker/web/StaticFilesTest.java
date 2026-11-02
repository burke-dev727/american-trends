package energytracker.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StaticFilesTest {

    private static Path directory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        directory = Files.createTempDirectory("energy-spa");
        Files.writeString(directory.resolve("index.html"), "<html>app</html>");
        Files.createDirectories(directory.resolve("assets"));
        Files.writeString(directory.resolve("assets").resolve("app-123.js"), "js");
        registry.add("app.static-dir", directory::toString);
        registry.add("app.data-source", () -> "eia");
        registry.add("app.openai.api-key", () -> "");
        registry.add("app.eia-api-key", () -> "");
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void servesTheFrontendWithoutShadowingTheApi() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(result ->
                assertEquals("<html>app</html>", result.getResponse().getContentAsString()));
        mvc.perform(get("/assets/app-123.js")).andExpect(status().isOk()).andExpect(result ->
                assertFalse(result.getResponse().getHeader("Cache-Control").isBlank()));
        mvc.perform(get("/electricity")).andExpect(status().isOk()).andExpect(result ->
                assertEquals("<html>app</html>", result.getResponse().getContentAsString()));
        mvc.perform(get("/data-centers")).andExpect(status().isOk()).andExpect(result ->
                assertEquals("<html>app</html>", result.getResponse().getContentAsString()));
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/healthz")).andExpect(result ->
                assertEquals("ok", result.getResponse().getContentAsString()));
        mvc.perform(get("/api/energy/test")).andExpect(result ->
                assertEquals("Energy API is working", result.getResponse().getContentAsString()));
    }
}
