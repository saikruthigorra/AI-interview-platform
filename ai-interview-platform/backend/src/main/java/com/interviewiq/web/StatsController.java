package com.interviewiq.web;

import com.interviewiq.ai.AiService;
import com.interviewiq.config.AppProperties;
import com.interviewiq.service.StatsService;
import com.interviewiq.web.dto.StatsResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final StatsService stats;
    private final AiService ai;
    private final AppProperties props;
    private final Environment env;

    public StatsController(StatsService stats, AiService ai, AppProperties props, Environment env) {
        this.stats = stats;
        this.ai = ai;
        this.props = props;
        this.env = env;
    }

    @GetMapping("/stats")
    public StatsResponse overview() {
        return stats.overview();
    }

    /** Runtime configuration the UI surfaces (AI engine + active database profile). */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("aiEngine", ai.engineName());
        out.put("llmEnabled", ai.remoteEnabled());
        out.put("llmModel", ai.remoteEnabled() ? props.getAi().getModel() : null);
        String[] profiles = env.getActiveProfiles();
        out.put("profile", profiles.length == 0 ? "local" : profiles[0]);
        out.put("database", "tidb".equals(profiles.length == 0 ? "local" : profiles[0]) ? "TiDB" : "H2 (MySQL mode)");
        out.put("version", "0.1.0");
        return out;
    }
}
