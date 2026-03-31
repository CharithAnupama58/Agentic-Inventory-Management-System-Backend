package com.pos.system.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ChatDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChatRequest {
        private String              message;
        private List<ChatMessage>   history;    // previous messages
        private String              sessionId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChatMessage {
        private String role;     // user | assistant
        private String content;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChatResponse {
        private String            message;       // AI reply text
        private String            sessionId;
        private List<AgentAction> actions;       // actions taken
        private List<ActionResult> results;      // action results
        private String            intent;        // detected intent
        private boolean           actionsTaken;
        private LocalDateTime     timestamp;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AgentAction {
        private String              type;        // CREATE_CAMPAIGN, ADJUST_STOCK etc
        private String              description; // human readable
        private Map<String, Object> parameters;  // action params
        private String              status;      // PENDING EXECUTED FAILED
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ActionResult {
        private String  action;
        private boolean success;
        private String  message;
        private Object  data;
    }
}
