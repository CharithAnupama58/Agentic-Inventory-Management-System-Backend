package com.pos.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.system.dto.ChatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessChatAgentService {

    private final AgentToolsService agentToolsService;
    private final ObjectMapper      objectMapper;

    @Value("${ai.groq.api.key:}")
    private String groqKey;

    @Value("${ai.groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    // ── Process chat message ──────────────────────────────────────────────────
    public ChatDto.ChatResponse chat(ChatDto.ChatRequest request) {
        log.info("Chat request: {}", request.getMessage());

        // ── Step 1: Gather business context ──────────────────────────────────
        Map<String, Object> context = gatherContext();

        // ── Step 2: Ask AI what actions to take ──────────────────────────────
        String agentPlan = getAgentPlan(request, context);

        // ── Step 3: Parse and execute actions ────────────────────────────────
        List<ChatDto.AgentAction>  actions = new ArrayList<>();
        List<ChatDto.ActionResult> results = new ArrayList<>();
        String intent = "QUERY";

        try {
            Map<String, Object> plan = objectMapper.readValue(
                    agentPlan, new TypeReference<>() {});

            intent = (String) plan.getOrDefault("intent", "QUERY");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plannedActions =
                    (List<Map<String, Object>>) plan.getOrDefault(
                            "actions", new ArrayList<>());

            for (Map<String, Object> action : plannedActions) {
                String actionType = (String) action.get("type");
                ChatDto.ActionResult result =
                        executeAction(actionType, action);
                results.add(result);

                actions.add(ChatDto.AgentAction.builder()
                        .type(actionType)
                        .description((String) action.getOrDefault(
                                "description", actionType))
                        .parameters(action)
                        .status(result.isSuccess() ? "EXECUTED" : "FAILED")
                        .build());
            }

        } catch (Exception e) {
            log.error("Error parsing agent plan: {}", e.getMessage());
        }

        // ── Step 4: Generate final response ──────────────────────────────────
        String finalMessage = generateFinalResponse(
                request.getMessage(), actions, results, context);

        return ChatDto.ChatResponse.builder()
                .message(finalMessage)
                .sessionId(request.getSessionId() != null
                        ? request.getSessionId()
                        : UUID.randomUUID().toString())
                .actions(actions)
                .results(results)
                .intent(intent)
                .actionsTaken(!actions.isEmpty())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ── Gather live business context ──────────────────────────────────────────
    private Map<String, Object> gatherContext() {
        Map<String, Object> ctx = new HashMap<>();
        try {
            ctx.put("snapshot",       agentToolsService.getBusinessSnapshot());
            ctx.put("slowMoving",     agentToolsService
                    .findSlowMovingProducts(30));
            ctx.put("lowStock",       agentToolsService
                    .findLowStockProducts(10));
            ctx.put("outOfStock",     agentToolsService
                    .findOutOfStockProducts());
            ctx.put("topProducts",    agentToolsService
                    .getTopSellingProducts(5));
            ctx.put("activeCampaigns",agentToolsService
                    .getActiveCampaigns());
            ctx.put("reorderStatus",  agentToolsService
                    .triggerReorderAnalysis());
        } catch (Exception e) {
            log.error("Error gathering context: {}", e.getMessage());
        }
        return ctx;
    }

    // ── Ask Groq what actions to take ─────────────────────────────────────────
    private String getAgentPlan(ChatDto.ChatRequest request,
                                  Map<String, Object> context) {
        if (groqKey == null || groqKey.isBlank()) {
            return buildRuleBasedPlan(request.getMessage(), context);
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders  headers      = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqKey);

            String systemPrompt = buildSystemPrompt(context);
            String userPrompt   = buildUserPrompt(
                    request.getMessage(),
                    request.getHistory());

            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("role",    "system");
            sysMsg.put("content", systemPrompt);

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role",    "user");
            userMsg.put("content", userPrompt);

            Map<String, Object> body = new HashMap<>();
            body.put("model",       "llama-3.3-70b-versatile");
            body.put("messages",    List.of(sysMsg, userMsg));
            body.put("max_tokens",  1000);
            body.put("temperature", 0.2);
            body.put("stream",      false);

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(groqUrl, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String text = root.path("choices").get(0)
                              .path("message").path("content").asText();

            // Extract JSON from response
            text = text.replaceAll("(?s)```json", "")
                       .replaceAll("```", "").trim();
            int start = text.indexOf("{");
            int end   = text.lastIndexOf("}");
            if (start >= 0 && end > start)
                return text.substring(start, end + 1);

            return "{\"intent\":\"QUERY\",\"actions\":[]}";

        } catch (Exception e) {
            log.error("Groq agent plan failed: {}", e.getMessage());
            return buildRuleBasedPlan(request.getMessage(), context);
        }
    }

    // ── System prompt with all available tools ────────────────────────────────
    private String buildSystemPrompt(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an autonomous business agent for a retail POS system.\n");
        sb.append("You can TAKE ACTIONS, not just answer questions.\n\n");

        sb.append("=== CURRENT BUSINESS STATE ===\n");
        try {
            sb.append(objectMapper.writeValueAsString(context)).append("\n\n");
        } catch (Exception e) {
            sb.append("Context unavailable\n\n");
        }

        sb.append("=== AVAILABLE ACTIONS ===\n");
        sb.append("1. CREATE_CAMPAIGN — Create discount campaign\n");
        sb.append("   params: name, description, discountType(PERCENTAGE/FIXED), ");
        sb.append("discountValue, productIds[], durationDays, aiReason\n\n");

        sb.append("2. ADJUST_STOCK — Adjust product stock level\n");
        sb.append("   params: productId, quantity(+/-), reason\n\n");

        sb.append("3. GET_SLOW_PRODUCTS — Find slow moving products\n");
        sb.append("   params: days(default 30)\n\n");

        sb.append("4. GET_LOW_STOCK — Find products running low\n");
        sb.append("   params: threshold(default 10)\n\n");

        sb.append("5. GET_TOP_PRODUCTS — Get best sellers\n");
        sb.append("   params: limit(default 5)\n\n");

        sb.append("6. GET_CAMPAIGNS — List active campaigns\n");
        sb.append("   params: none\n\n");

        sb.append("7. ANALYZE_REORDERS — Check reorder status\n");
        sb.append("   params: none\n\n");

        sb.append("=== RESPONSE FORMAT ===\n");
        sb.append("Respond ONLY with valid JSON:\n");
        sb.append("{\n");
        sb.append("  \"intent\": \"ACTION|QUERY|REPORT\",\n");
        sb.append("  \"understanding\": \"what the user wants\",\n");
        sb.append("  \"actions\": [\n");
        sb.append("    {\n");
        sb.append("      \"type\": \"ACTION_TYPE\",\n");
        sb.append("      \"description\": \"what this action does\",\n");
        sb.append("      \"name\": \"...\",\n");
        sb.append("      \"description\": \"...\",\n");
        sb.append("      \"discountType\": \"PERCENTAGE\",\n");
        sb.append("      \"discountValue\": 20,\n");
        sb.append("      \"productIds\": [\"id1\", \"id2\"],\n");
        sb.append("      \"durationDays\": 7,\n");
        sb.append("      \"aiReason\": \"why this action\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("Rules:\n");
        sb.append("- If user asks to CREATE something, include the action\n");
        sb.append("- If user asks a question, use GET actions to fetch data\n");
        sb.append("- Use real product IDs from the context above\n");
        sb.append("- Keep actions minimal and relevant\n");
        sb.append("- For campaigns: use productIds from slowMoving products\n");

        return sb.toString();
    }

    private String buildUserPrompt(String message,
                                    List<ChatDto.ChatMessage> history) {
        StringBuilder sb = new StringBuilder();

        if (history != null && !history.isEmpty()) {
            sb.append("Conversation history:\n");
            history.stream().limit(6).forEach(h ->
                sb.append(h.getRole()).append(": ")
                  .append(h.getContent()).append("\n"));
            sb.append("\n");
        }

        sb.append("User request: ").append(message);
        return sb.toString();
    }

    // ── Execute a single action ───────────────────────────────────────────────
    private ChatDto.ActionResult executeAction(
            String actionType, Map<String, Object> params) {
        try {
            return switch (actionType) {
                case "CREATE_CAMPAIGN" -> {
                    @SuppressWarnings("unchecked")
                    List<String> productIds = (List<String>) params
                            .getOrDefault("productIds", new ArrayList<>());

                    if (productIds.isEmpty()) {
                        // Auto-fetch slow moving products
                        List<Map<String, Object>> slow = agentToolsService
                                .findSlowMovingProducts(30);
                        productIds = slow.stream()
                                .limit(10)
                                .map(p -> (String) p.get("id"))
                                .collect(java.util.stream.Collectors.toList());
                    }

                    Object dv = params.getOrDefault("discountValue", 20);
                    double discountVal = dv instanceof Number
                            ? ((Number) dv).doubleValue() : 20.0;

                    Object dd = params.getOrDefault("durationDays", 7);
                    int durationDays = dd instanceof Number
                            ? ((Number) dd).intValue() : 7;

                    Map<String, Object> result =
                            agentToolsService.createCampaign(
                                (String) params.getOrDefault("name",
                                        "AI Campaign"),
                                (String) params.getOrDefault("description",
                                        "Auto-generated campaign"),
                                (String) params.getOrDefault("discountType",
                                        "PERCENTAGE"),
                                discountVal,
                                productIds,
                                durationDays,
                                (String) params.getOrDefault("aiReason",
                                        "AI recommended"));

                    yield ChatDto.ActionResult.builder()
                            .action("CREATE_CAMPAIGN")
                            .success(true)
                            .message("✅ Campaign created successfully!")
                            .data(result)
                            .build();
                }

                case "ADJUST_STOCK" -> {
                    Object qty = params.getOrDefault("quantity", 0);
                    int quantity = qty instanceof Number
                            ? ((Number) qty).intValue() : 0;

                    Map<String, Object> result =
                            agentToolsService.adjustProductStock(
                                (String) params.get("productId"),
                                quantity,
                                (String) params.getOrDefault("reason",
                                        "AI adjustment"));

                    yield ChatDto.ActionResult.builder()
                            .action("ADJUST_STOCK")
                            .success(true)
                            .message("✅ Stock adjusted")
                            .data(result)
                            .build();
                }

                case "GET_SLOW_PRODUCTS" -> {
                    Object d = params.getOrDefault("days", 30);
                    int days = d instanceof Number
                            ? ((Number) d).intValue() : 30;
                    List<Map<String, Object>> slow = agentToolsService
                            .findSlowMovingProducts(days);
                    yield ChatDto.ActionResult.builder()
                            .action("GET_SLOW_PRODUCTS")
                            .success(true)
                            .message("Found " + slow.size()
                                    + " slow-moving products")
                            .data(slow)
                            .build();
                }

                case "GET_LOW_STOCK" -> {
                    Object t = params.getOrDefault("threshold", 10);
                    int threshold = t instanceof Number
                            ? ((Number) t).intValue() : 10;
                    List<Map<String, Object>> low = agentToolsService
                            .findLowStockProducts(threshold);
                    yield ChatDto.ActionResult.builder()
                            .action("GET_LOW_STOCK")
                            .success(true)
                            .message("Found " + low.size()
                                    + " low-stock products")
                            .data(low)
                            .build();
                }

                case "GET_TOP_PRODUCTS" -> {
                    Object l = params.getOrDefault("limit", 5);
                    int limit = l instanceof Number
                            ? ((Number) l).intValue() : 5;
                    List<Map<String, Object>> top = agentToolsService
                            .getTopSellingProducts(limit);
                    yield ChatDto.ActionResult.builder()
                            .action("GET_TOP_PRODUCTS")
                            .success(true)
                            .message("Top " + top.size() + " products")
                            .data(top)
                            .build();
                }

                case "GET_CAMPAIGNS" -> {
                    List<Map<String, Object>> campaigns = agentToolsService
                            .getActiveCampaigns();
                    yield ChatDto.ActionResult.builder()
                            .action("GET_CAMPAIGNS")
                            .success(true)
                            .message(campaigns.size() + " active campaigns")
                            .data(campaigns)
                            .build();
                }

                case "ANALYZE_REORDERS" -> {
                    Map<String, Object> reorders = agentToolsService
                            .triggerReorderAnalysis();
                    yield ChatDto.ActionResult.builder()
                            .action("ANALYZE_REORDERS")
                            .success(true)
                            .message("Reorder analysis complete")
                            .data(reorders)
                            .build();
                }

                default -> ChatDto.ActionResult.builder()
                        .action(actionType)
                        .success(false)
                        .message("Unknown action: " + actionType)
                        .build();
            };
        } catch (Exception e) {
            log.error("Action {} failed: {}", actionType, e.getMessage());
            return ChatDto.ActionResult.builder()
                    .action(actionType)
                    .success(false)
                    .message("Failed: " + e.getMessage())
                    .build();
        }
    }

    // ── Generate final human-readable response ────────────────────────────────
    private String generateFinalResponse(
            String userMessage,
            List<ChatDto.AgentAction> actions,
            List<ChatDto.ActionResult> results,
            Map<String, Object> context) {

        if (groqKey == null || groqKey.isBlank()) {
            return buildRuleBasedResponse(userMessage, actions, results);
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders  headers      = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqKey);

            StringBuilder prompt = new StringBuilder();
            prompt.append("User asked: ").append(userMessage).append("\n\n");
            prompt.append("Actions taken:\n");
            for (int i = 0; i < actions.size(); i++) {
                prompt.append("- ").append(actions.get(i).getDescription())
                      .append(": ").append(results.get(i).getMessage())
                      .append("\n");
                if (results.get(i).getData() != null) {
                    try {
                        prompt.append("  Data: ")
                              .append(objectMapper.writeValueAsString(
                                      results.get(i).getData())
                                      .substring(0, Math.min(300,
                                      objectMapper.writeValueAsString(
                                              results.get(i).getData())
                                              .length())))
                              .append("\n");
                    } catch (Exception ignored) {}
                }
            }
            prompt.append("\nWrite a friendly, concise response (2-4 sentences) ");
            prompt.append("summarizing what was done and any key findings. ");
            prompt.append("Use emojis. Be specific with numbers.");

            Map<String, Object> msg = new HashMap<>();
            msg.put("role",    "user");
            msg.put("content", prompt.toString());

            Map<String, Object> body = new HashMap<>();
            body.put("model",       "llama-3.3-70b-versatile");
            body.put("messages",    List.of(msg));
            body.put("max_tokens",  300);
            body.put("temperature", 0.5);
            body.put("stream",      false);

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(groqUrl, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0)
                       .path("message").path("content").asText();

        } catch (Exception e) {
            log.error("Final response generation failed: {}", e.getMessage());
            return buildRuleBasedResponse(userMessage, actions, results);
        }
    }

    // ── Rule-based plan (no API key) ──────────────────────────────────────────
    private String buildRuleBasedPlan(String message,
                                       Map<String, Object> context) {
        String lower = message.toLowerCase();

        if (lower.contains("slow") || lower.contains("campaign")
                || lower.contains("discount") || lower.contains("promotion")) {
            return "{\"intent\":\"ACTION\","
                + "\"understanding\":\"Create discount campaign\","
                + "\"actions\":[{"
                + "\"type\":\"CREATE_CAMPAIGN\","
                + "\"description\":\"Create 20% discount for slow movers\","
                + "\"name\":\"Clearance Campaign\","
                + "\"discountType\":\"PERCENTAGE\","
                + "\"discountValue\":20,"
                + "\"durationDays\":7,"
                + "\"aiReason\":\"Slow moving products need promotion\""
                + "}]}";
        }

        if (lower.contains("low stock") || lower.contains("restock")
                || lower.contains("running out")) {
            return "{\"intent\":\"QUERY\","
                + "\"understanding\":\"Check low stock\","
                + "\"actions\":[{"
                + "\"type\":\"GET_LOW_STOCK\","
                + "\"description\":\"Find low stock products\","
                + "\"threshold\":10"
                + "}]}";
        }

        if (lower.contains("top") || lower.contains("best selling")
                || lower.contains("popular")) {
            return "{\"intent\":\"QUERY\","
                + "\"understanding\":\"Get top products\","
                + "\"actions\":[{"
                + "\"type\":\"GET_TOP_PRODUCTS\","
                + "\"description\":\"Get best selling products\","
                + "\"limit\":5"
                + "}]}";
        }

        if (lower.contains("campaign") || lower.contains("promotion")) {
            return "{\"intent\":\"QUERY\","
                + "\"understanding\":\"List campaigns\","
                + "\"actions\":[{"
                + "\"type\":\"GET_CAMPAIGNS\","
                + "\"description\":\"Get active campaigns\""
                + "}]}";
        }

        return "{\"intent\":\"QUERY\",\"understanding\":\""
                + message + "\",\"actions\":[]}";
    }

    private String buildRuleBasedResponse(
            String message,
            List<ChatDto.AgentAction> actions,
            List<ChatDto.ActionResult> results) {

        if (actions.isEmpty()) {
            return "I understand you're asking about: **" + message
                    + "**. To get the most out of me, try asking me to:\n"
                    + "- 'Create a discount campaign for slow products'\n"
                    + "- 'Show me products running low on stock'\n"
                    + "- 'What are my top selling products?'\n"
                    + "- 'Analyze my reorder situation'";
        }

        StringBuilder response = new StringBuilder();
        for (ChatDto.ActionResult result : results) {
            if (result.isSuccess()) {
                response.append("✅ ").append(result.getMessage()).append("\n");
            } else {
                response.append("❌ ").append(result.getMessage()).append("\n");
            }
        }

        return response.toString().trim();
    }
}
