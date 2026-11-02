package energytracker.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import energytracker.energy.EiaEnergySource;
import energytracker.energy.EiaRecord;
import energytracker.energy.EnergyMix;
import energytracker.energy.EnergySource;
import energytracker.energy.Forecast;
import energytracker.energy.Milestone;
import energytracker.energy.Periods;
import energytracker.energy.SourceTrend;
import energytracker.energy.TrendPoint;
import energytracker.energy.TrendSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EnergyApiTest {

    @Test
    void healthAndSampleRoutes() throws Exception {
        MockMvc mvc = mvc(new EiaEnergySource((start, end) -> List.of()));
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(result ->
                assertEquals("ok", result.getResponse().getContentAsString()));
        mvc.perform(get("/api/energy/test")).andExpect(status().isOk()).andExpect(result ->
                assertEquals("Energy API is working", result.getResponse().getContentAsString()));
        mvc.perform(get("/api/energy/mix/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naturalGas").value(15.4));
    }

    @Test
    void realRouteAggregatesAndRejectsBadPeriods() throws Exception {
        MockMvc mvc = mvc(new EiaEnergySource((start, end) -> List.of(
                new EiaRecord(start, "ALL", "100"),
                new EiaRecord(start, "COW", "25"))));
        mvc.perform(get("/api/energy/real/2024-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coal").value(25))
                .andExpect(jsonPath("$.other").value(75));

        mvc.perform(get("/api/energy/real/2024-13")).andExpect(status().isBadRequest()).andExpect(result ->
                assertEquals(Periods.INVALID, result.getResponse().getContentAsString().trim()));
    }

    @Test
    void rangeSortsMonths() throws Exception {
        MockMvc mvc = mvc(new EiaEnergySource((start, end) -> List.of(
                new EiaRecord("2024-02", "ALL", "200"),
                new EiaRecord("2024-02", "SUN", "50"),
                new EiaRecord("2024-01", "ALL", "100"),
                new EiaRecord("2024-01", "NG", "40"))));
        mvc.perform(get("/api/energy/range").param("start", "2024-01").param("end", "2024-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].period").value("2024-01"))
                .andExpect(jsonPath("$[0].naturalGas").value(40))
                .andExpect(jsonPath("$[1].period").value("2024-02"))
                .andExpect(jsonPath("$[1].solar").value(25));
        mvc.perform(get("/api/energy/range").param("start", "2024-1").param("end", "2024-02"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trendsAreMissingFromTheLiveEiaSource() throws Exception {
        MockMvc mvc = mvc(new EiaEnergySource((start, end) -> List.of()));
        for (String path : List.of(
                "/api/energy/trends?start=2024-01&end=2024-02",
                "/api/energy/milestones",
                "/api/energy/forecast")) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void trendRoutesReadThePrecomputedSource() throws Exception {
        TrendSource source = new FixedTrends(
                List.of(
                        new TrendPoint("2024-01", Map.of("solar", new SourceTrend(0, 4, null, null))),
                        new TrendPoint("2024-02", Map.of("solar", new SourceTrend(0, 5, null, null)))),
                List.of(new Milestone("2024-02", "solar", "hydro", 5, "Solar passes hydro")),
                new Forecast("2024-02", List.of(
                        new TrendPoint("2024-03", Map.of("solar", new SourceTrend(0, 6, null, null))))));
        MockMvc mvc = mvc(source);
        mvc.perform(get("/api/energy/trends").param("start", "2024-02").param("end", "2024-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sources.solar.share").value(5));
        mvc.perform(get("/api/energy/trends").param("start", "bad").param("end", "2024-02"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/energy/milestones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passedSource").value("hydro"));
        mvc.perform(get("/api/energy/forecast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vintage").value("2024-02"))
                .andExpect(jsonPath("$.points[0].sources.solar.share").value(6));
    }

    @Test
    void emptyMonthIsReturnedAsZeros() throws Exception {
        MockMvc mvc = mvc((start, end) -> List.of());
        mvc.perform(get("/api/energy/real/2024-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("2024-01"))
                .andExpect(jsonPath("$.coal").value(0));
    }

    private static MockMvc mvc(EnergySource source) {
        return MockMvcBuilders.standaloneSetup(new EnergyController(source))
                .setMessageConverters(
                        new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter())
                .build();
    }

    private record FixedTrends(List<TrendPoint> points, List<Milestone> milestones, Forecast forecast)
            implements TrendSource {
        @Override
        public List<EnergyMix> energyMixes(String start, String end) {
            return List.of();
        }

        @Override
        public List<TrendPoint> energyTrends(String start, String end) {
            return Periods.inRange(points, start, end, TrendPoint::period);
        }

        @Override
        public List<Milestone> energyMilestones() {
            return milestones;
        }

        @Override
        public Forecast energyForecast() {
            return forecast;
        }
    }
}
