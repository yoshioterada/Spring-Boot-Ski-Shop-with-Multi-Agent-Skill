package com.example.skishop.agent.common.invoker;

import com.example.skishop.agent.common.dto.SkiFeasibilityResult;

/**
 * Worker→Worker 呼び出し抽象（Equipment Matching / Dynamic Pricing → Weather）。
 *
 * <p>weather-agent モジュールが {@code LocalWeatherInvoker}（モノリス用）を提供し、
 * 各利用側 Agent モジュールが {@code RemoteWeatherInvoker}（分散用、@ConditionalOnProperty(distributed)）を提供する。
 *
 * <p>注: Orchestrator は本インターフェースではなく {@code WorkerAgentInvoker} を介して
 * Weather Agent を呼ぶため、analyze/getSkiConditions/getCurrent 等の幅広い API は
 * 本抽象には含めない（YAGNI 原則）。新規ユースケースで必要になった時点で追加する。
 */
public interface WeatherInvoker {

    /** スキー適性評価（spec score・装備推奨レベル・気象総合スコア）を返す。Equipment Matching が使用。 */
    SkiFeasibilityResult getFeasibility(String location);

    /**
     * 気象総合判定（EXCELLENT/GOOD/POOR）。Dynamic Pricing が価格係数決定に使用。
     * デフォルト実装は {@link #getFeasibility(String)} の {@code overallCondition} を返す。
     */
    default String getOverallCondition(String location) {
        return getFeasibility(location).overallCondition();
    }
}
