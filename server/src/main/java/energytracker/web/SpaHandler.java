package energytracker.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Serves the built frontend when {@code STATIC_DIR} is set. Files under {@code /assets/}
 * are cached indefinitely. Extensionless paths with no file receive {@code index.html}
 * so client-side routes survive a refresh. Registered after the API mappings.
 */
@Component
public class SpaHandler implements HandlerMapping, Ordered {

    private final Path root;

    public SpaHandler(energytracker.config.AppProperties properties) {
        String dir = properties.staticDir();
        this.root = dir == null || dir.isBlank() ? null : Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public HandlerExecutionChain getHandler(HttpServletRequest request) {
        if (root == null || !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/api/") || "/healthz".equals(path)) {
            return null;
        }
        HttpRequestHandler handler = (req, res) -> serve(req, res);
        return new HandlerExecutionChain(handler);
    }

    private void serve(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        boolean asset = path.startsWith("/assets/");
        Path file = resolve(path);
        boolean exists = file != null && Files.isRegularFile(file);
        if (!exists) {
            if (asset || hasExtension(path) || file == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            file = root.resolve("index.html");
            if (!Files.isRegularFile(file)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }
        if (asset) {
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        }
        MediaType type = MediaTypeFactory.getMediaType(file.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        response.setContentType(type.toString());
        if ("HEAD".equals(request.getMethod())) {
            response.setContentLengthLong(Files.size(file));
            return;
        }
        try (var out = response.getOutputStream()) {
            Files.copy(file, out);
        }
    }

    private Path resolve(String urlPath) {
        String decoded;
        try {
            decoded = URLDecoder.decode(urlPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String relative = decoded.replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            return null;
        }
        return resolved;
    }

    private static boolean hasExtension(String urlPath) {
        int slash = urlPath.lastIndexOf('/');
        String name = slash >= 0 ? urlPath.substring(slash + 1) : urlPath;
        return name.indexOf('.') >= 0;
    }
}
