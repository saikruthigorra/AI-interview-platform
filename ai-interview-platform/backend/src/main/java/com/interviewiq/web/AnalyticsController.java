package com.interviewiq.web;

import com.interviewiq.service.AnalyticsService;
import com.interviewiq.web.dto.AnalyticsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    /** One call, whole dashboard: outcomes + concept analysis + review sentiment. */
    @GetMapping
    public AnalyticsResponse dashboard() {
        return analytics.dashboard();
    }
}
