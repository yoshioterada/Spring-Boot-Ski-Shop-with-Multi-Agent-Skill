package com.example.skishop.ai.scheduler;

import com.example.skishop.ai.config.AiAnalyzerProperties;
import com.example.skishop.ai.service.WeeklySummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * F3 週次サマリーバッチスケジューラ (D-F3-01: 毎週月曜 07:00 JST).
 * <p>
 * 起動時にも当週分がなければ即時生成 (impl-plan P1.2.1).
 */
@Component
public class WeeklySummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklySummaryScheduler.class);

    private final WeeklySummaryService weeklySummaryService;
    private final AiAnalyzerProperties props;

    public WeeklySummaryScheduler(WeeklySummaryService weeklySummaryService,
                                  AiAnalyzerProperties props) {
        this.weeklySummaryService = weeklySummaryService;
        this.props = props;
    }

    /**
     * 毎週月曜 07:00 JST に発火 (cron + zone は application.properties 外出し).
     */
    @Scheduled(cron = "${ai-analyzer.weekly-summary.cron}", zone = "${ai-analyzer.weekly-summary.zone}")
    public void scheduledGenerate() {
        if (!props.isEnabled()) {
            log.info("AI Analyzer disabled, skipping weekly summary generation");
            return;
        }
        log.info("Scheduled weekly summary generation triggered");
        weeklySummaryService.ensureCurrentWeek();
    }

    /**
     * アプリ起動時に当週分がなければ即時生成.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!props.isEnabled()) {
            log.info("AI Analyzer disabled at startup");
            return;
        }
        log.info("Startup: checking if current week summary exists");
        weeklySummaryService.ensureCurrentWeek();
    }
}
