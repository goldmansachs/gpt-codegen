package org.rj.modelgen.llm.integrations;

import reactor.core.publisher.Mono;

public interface ConversationProvider {
    Mono<String> createConversation(String authToken, String title, String baseLlm);
}
