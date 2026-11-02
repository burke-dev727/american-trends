package energytracker.web;

import energytracker.energy.EnergyMix;
import energytracker.energy.EnergySource;
import energytracker.energy.Periods;
import energytracker.energy.TrendSource;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!export")
public class EnergyController {

    private final EnergySource source;

    public EnergyController(EnergySource source) {
        this.source = source;
    }

    @GetMapping(value = "/healthz", produces = "text/plain")
    public String health() {
        return "ok";
    }

    @GetMapping(value = "/api/energy/test", produces = "text/plain")
    public String test() {
        return "Energy API is working";
    }

    @GetMapping("/api/energy/mix/test")
    public EnergyMix testMix() {
        return EnergyMix.sample();
    }

    @GetMapping("/api/energy/real/{period}")
    public ResponseEntity<?> real(@PathVariable String period) {
        if (!Periods.valid(period)) {
            return TextResponses.status(org.springframework.http.HttpStatus.BAD_REQUEST, Periods.INVALID);
        }
        try {
            List<EnergyMix> mixes = source.energyMixes(period, period);
            return ResponseEntity.ok(mixes.isEmpty() ? EnergyMix.empty(period) : mixes.get(0));
        } catch (RuntimeException ex) {
            return TextResponses.upstream(ex);
        }
    }

    @GetMapping("/api/energy/range")
    public ResponseEntity<?> range(@RequestParam(required = false) String start, @RequestParam(required = false) String end) {
        if (!Periods.valid(start) || !Periods.valid(end)) {
            return TextResponses.status(org.springframework.http.HttpStatus.BAD_REQUEST, Periods.INVALID);
        }
        try {
            return ResponseEntity.ok(source.energyMixes(start, end));
        } catch (RuntimeException ex) {
            return TextResponses.upstream(ex);
        }
    }

    @GetMapping("/api/energy/trends")
    public ResponseEntity<?> trends(@RequestParam(required = false) String start, @RequestParam(required = false) String end) {
        if (!(source instanceof TrendSource trends)) {
            return TextResponses.status(org.springframework.http.HttpStatus.NOT_FOUND, TextResponses.TRENDS_UNAVAILABLE);
        }
        if (!Periods.valid(start) || !Periods.valid(end)) {
            return TextResponses.status(org.springframework.http.HttpStatus.BAD_REQUEST, Periods.INVALID);
        }
        try {
            return ResponseEntity.ok(trends.energyTrends(start, end));
        } catch (RuntimeException ex) {
            return TextResponses.upstream(ex);
        }
    }

    @GetMapping("/api/energy/milestones")
    public ResponseEntity<?> milestones() {
        if (!(source instanceof TrendSource trends)) {
            return TextResponses.status(org.springframework.http.HttpStatus.NOT_FOUND, TextResponses.TRENDS_UNAVAILABLE);
        }
        try {
            return ResponseEntity.ok(trends.energyMilestones());
        } catch (RuntimeException ex) {
            return TextResponses.upstream(ex);
        }
    }

    @GetMapping("/api/energy/forecast")
    public ResponseEntity<?> forecast() {
        if (!(source instanceof TrendSource trends)) {
            return TextResponses.status(org.springframework.http.HttpStatus.NOT_FOUND, TextResponses.TRENDS_UNAVAILABLE);
        }
        try {
            return ResponseEntity.ok(trends.energyForecast());
        } catch (RuntimeException ex) {
            return TextResponses.upstream(ex);
        }
    }
}
