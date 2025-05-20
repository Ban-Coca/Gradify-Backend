package com.capstone.gradify.Service.AiServices;

import com.anthropic.models.messages.MessageCreateParams;
import org.springframework.stereotype.Service;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

@Service
public class GenerateFeedbackAIService {
    AnthropicClient client = AnthropicOkHttpClient.fromEnv();

    public String generateFeedbackAI(String prompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model("claude-3-5-haiku-20241022")
                .addUserMessage(prompt)
                .maxTokens(8192)
                .temperature(1)
                .build();
        return "message";

    }
}
