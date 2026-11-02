package energytracker.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class TextResponses {

    static final String UPSTREAM = "Failed to load energy data";
    static final String TRENDS_UNAVAILABLE =
            "Trends, milestones, and forecasts require DATA_SOURCE=databricks or snapshot";

    private static final Logger log = LoggerFactory.getLogger(TextResponses.class);

    private TextResponses() {}

    static ResponseEntity<String> status(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message + "\n");
    }

    static ResponseEntity<String> upstream(RuntimeException ex) {
        log.warn("energy data request failed: {}", ex.toString());
        return status(HttpStatus.BAD_GATEWAY, UPSTREAM);
    }
}
