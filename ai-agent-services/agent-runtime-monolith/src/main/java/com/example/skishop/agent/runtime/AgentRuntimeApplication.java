package com.example.skishop.agent.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Multi-Agent System モノリス実行可能 Spring Boot アプリ。
 * 各 Agent モジュールの {@code AutoConfiguration} が META-INF/spring/...AutoConfiguration.imports
 * 経由で自動的に読み込まれるため、本クラスでは追加のコンポーネントスキャンは不要。
 */
@SpringBootApplication
public class AgentRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentRuntimeApplication.class, args);
    }
}
