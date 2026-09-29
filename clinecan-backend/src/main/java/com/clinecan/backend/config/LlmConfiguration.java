package com.clinecan.backend.config;
import com.clinecan.backend.agent.*;
import com.clinecan.backend.llm.*;
import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class LlmConfiguration {
    @Bean
    public LlmClient llmClient(Environment env, PromptAnalyzer analyzer, ProjectPlanner planner, CodeGenerator generator) {
        String key = env.getProperty("clinecan.llm.api-key", "");
        if (key.isBlank()) return new DemoLlmClient(analyzer, planner, generator);
        long timeout;
        try { timeout = Long.parseLong(env.getProperty("clinecan.llm.timeout-seconds", "45")); }
        catch (NumberFormatException e) { timeout = 0; }
        return new OpenAiLlmClient(env.getProperty("clinecan.llm.endpoint", "https://api.openai.com/v1"), key,
            env.getProperty("clinecan.llm.model", "gpt-4o-mini"), Duration.ofSeconds(timeout));
    }
}
