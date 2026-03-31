package com.pos.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.system.dto.InsightsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIInsightsService {

    private final SalesContextService salesContextService;
    private final ObjectMapper        objectMapper;

    @Value("${ai.groq.api.key:}")
    private String groqKey;

    @Value("${ai.groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    // ── Entry point ───────────────────────────────────────────────────────────
    public InsightsDto.InsightResponse generateInsights() {
        InsightsDto.SalesContext ctx = salesContextService.buildContext();

        if (groqKey == null || groqKey.isBlank()
                || groqKey.equals("gsk_PASTE_YOUR_GROQ_KEY_HERE")) {
            log.info("Groq key not set — using rule-based insights");
            return buildRuleBasedInsights(ctx);
        }

        try {
            return callGroq(ctx);
        } catch (Exception e) {
            log.error("Groq API failed: {} — falling back to rule-based",
                    e.getMessage());
            return buildRuleBasedInsights(ctx);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROQ API CALL
    // ─────────────────────────────────────────────────────────────────────────
    private InsightsDto.InsightResponse callGroq(
            InsightsDto.SalesContext ctx) throws Exception {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqKey);

        // ── System message ────────────────────────────────────────────────────
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content",
                "You are a smart business analyst for a retail POS system. "
                + "Always respond with ONLY valid raw JSON. "
                + "Never use markdown, code blocks, or any explanation. "
                + "Start your response directly with { and end with }.");

        // ── User message ──────────────────────────────────────────────────────
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role",    "user");
        userMessage.put("content", buildPrompt(ctx));

        // ── Request body ──────────────────────────────────────────────────────
        Map<String, Object> body = new HashMap<>();
        body.put("model",       "llama-3.3-70b-versatile");
        body.put("messages",    List.of(systemMessage, userMessage));
        body.put("max_tokens",  1500);
        body.put("temperature", 0.3);
        body.put("top_p",       0.8);
        body.put("stream",      false);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(body, headers);

        log.info("Calling Groq API with model: llama-3.3-70b-versatile");

        ResponseEntity<String> response =
                restTemplate.postForEntity(groqUrl, entity, String.class);

        log.info("Groq response status: {}", response.getStatusCode());

        return parseGroqResponse(response.getBody(), ctx);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PARSE GROQ RESPONSE
    // ─────────────────────────────────────────────────────────────────────────
    private InsightsDto.InsightResponse parseGroqResponse(
            String rawResponse, InsightsDto.SalesContext ctx)
            throws Exception {

        log.debug("Raw Groq response: {}", rawResponse);

        JsonNode root = objectMapper.readTree(rawResponse);

        // ── Check for API errors ──────────────────────────────────────────────
        if (root.has("error")) {
            String errorMsg = root.path("error")
                                  .path("message").asText();
            log.error("Groq API error: {}", errorMsg);
            throw new RuntimeException("Groq error: " + errorMsg);
        }

        // ── Extract text from response ────────────────────────────────────────
        String text = root.path("choices")
                          .get(0)
                          .path("message")
                          .path("content")
                          .asText();

        log.debug("Groq text response: {}", text);

        // ── Clean markdown if model added it ──────────────────────────────────
        text = text.replaceAll("(?s)```json", "")
                   .replaceAll("```", "")
                   .trim();

        // ── Extract JSON object ───────────────────────────────────────────────
        int start = text.indexOf("{");
        int end   = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        } else {
            log.error("No JSON found in Groq response: {}", text);
            throw new RuntimeException("No JSON in Groq response");
        }

        JsonNode parsed = objectMapper.readTree(text);

        // ── Build insights list ───────────────────────────────────────────────
        List<InsightsDto.Insight> insights = new ArrayList<>();
        parsed.path("insights").forEach(node ->
            insights.add(InsightsDto.Insight.builder()
                    .type(node.path("type").asText("NEUTRAL"))
                    .icon(node.path("icon").asText("📊"))
                    .title(node.path("title").asText(""))
                    .detail(node.path("detail").asText(""))
                    .build())
        );

        // ── Build alerts list ─────────────────────────────────────────────────
        List<String> alerts = new ArrayList<>();
        parsed.path("alerts").forEach(node -> alerts.add(node.asText()));

        // ── Build recommendations list ────────────────────────────────────────
        List<String> recommendations = new ArrayList<>();
        parsed.path("recommendations")
              .forEach(node -> recommendations.add(node.asText()));

        log.info("Groq insights parsed — {} insights, {} alerts, "
                + "{} recommendations",
                insights.size(), alerts.size(), recommendations.size());

        return InsightsDto.InsightResponse.builder()
                .summary(parsed.path("summary").asText(""))
                .insights(insights)
                .alerts(alerts)
                .recommendations(recommendations)
                .rawData(ctx)
                .provider("Groq Llama 3.3 70B")
                .generatedAt(LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROMPT BUILDER
    // ─────────────────────────────────────────────────────────────────────────
    private String buildPrompt(InsightsDto.SalesContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("Analyze this retail business sales data:\n\n");

        sb.append("TODAY:\n");
        sb.append("- Revenue: $").append(ctx.getTodayRevenue()).append("\n");
        sb.append("- Profit: $").append(ctx.getTodayProfit()).append("\n");
        sb.append("- Transactions: ").append(ctx.getTodayTransactions())
          .append("\n");
        sb.append("- Refunds: $").append(ctx.getTodayRefunds()).append("\n");
        sb.append("- Peak Hour: ").append(ctx.getPeakHourToday()).append("\n");
        sb.append("- Change vs Yesterday: ")
          .append(ctx.getRevenueGrowthVsYesterday()).append("%\n\n");

        sb.append("YESTERDAY:\n");
        sb.append("- Revenue: $").append(ctx.getYesterdayRevenue())
          .append("\n");
        sb.append("- Transactions: ").append(ctx.getYesterdayTransactions())
          .append("\n\n");

        sb.append("THIS WEEK:\n");
        sb.append("- Revenue: $").append(ctx.getWeekRevenue()).append("\n");
        sb.append("- Profit: $").append(ctx.getWeekProfit()).append("\n");
        sb.append("- Transactions: ").append(ctx.getWeekTransactions())
          .append("\n\n");

        sb.append("THIS MONTH:\n");
        sb.append("- Revenue: $").append(ctx.getMonthRevenue()).append("\n");
        sb.append("- Profit: $").append(ctx.getMonthProfit()).append("\n");
        sb.append("- Profit Margin: ").append(ctx.getProfitMargin())
          .append("%\n");
        sb.append("- Change vs Last Month: ")
          .append(ctx.getRevenueGrowthVsLastMonth()).append("%\n\n");

        sb.append("TOP PRODUCTS TODAY:\n");
        if (ctx.getTopProductsToday() != null
                && !ctx.getTopProductsToday().isEmpty()) {
            for (InsightsDto.ProductStat p : ctx.getTopProductsToday()) {
                sb.append("- ").append(p.getName())
                  .append(": ").append(p.getQuantitySold()).append(" sold")
                  .append(", $").append(p.getRevenue())
                  .append(", stock: ").append(p.getCurrentStock())
                  .append("\n");
            }
        } else {
            sb.append("- No sales today yet\n");
        }
        sb.append("\n");

        sb.append("INVENTORY:\n");
        sb.append("- Out of stock: ").append(ctx.getOutOfStockCount())
          .append("\n");
        sb.append("- Low stock: ").append(ctx.getLowStockCount())
          .append("\n");
        sb.append("- Critical reorders: ").append(ctx.getCriticalReorders())
          .append("\n");
        sb.append("- Slow moving products: ").append(ctx.getSlowMovingCount())
          .append("\n\n");

        sb.append("PAYMENT METHODS TODAY:\n");
        sb.append("- Cash: $").append(ctx.getCashRevenue()).append("\n");
        sb.append("- Card: $").append(ctx.getCardRevenue()).append("\n");
        sb.append("- Split: $").append(ctx.getSplitRevenue()).append("\n\n");

        sb.append("Respond with this exact JSON structure:\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"one sentence overall assessment\",\n");
        sb.append("  \"insights\": [\n");
        sb.append("    {\n");
        sb.append("      \"type\": \"POSITIVE\",\n");
        sb.append("      \"icon\": \"📈\",\n");
        sb.append("      \"title\": \"short title\",\n");
        sb.append("      \"detail\": \"detail with real numbers from the data\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"alerts\": [\"urgent issue\"],\n");
        sb.append("  \"recommendations\": [\"action 1\", \"action 2\", \"action 3\"]\n");
        sb.append("}\n\n");
        sb.append("Requirements:\n");
        sb.append("- Exactly 4 insights\n");
        sb.append("- type values: POSITIVE, NEGATIVE, NEUTRAL, WARNING\n");
        sb.append("- Use actual numbers from the data above\n");
        sb.append("- alerts only for urgent issues\n");
        sb.append("- 3 actionable recommendations\n");
        sb.append("- Simple non-technical language\n");

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RULE-BASED FALLBACK (no API key needed)
    // ─────────────────────────────────────────────────────────────────────────
    private InsightsDto.InsightResponse buildRuleBasedInsights(
            InsightsDto.SalesContext ctx) {

        List<InsightsDto.Insight> insights        = new ArrayList<>();
        List<String>              alerts          = new ArrayList<>();
        List<String>              recommendations = new ArrayList<>();

        BigDecimal growth = ctx.getRevenueGrowthVsYesterday();

        // ── Revenue vs yesterday ──────────────────────────────────────────────
        if (growth.compareTo(BigDecimal.ZERO) > 0) {
            insights.add(InsightsDto.Insight.builder()
                    .type("POSITIVE").icon("📈")
                    .title("Revenue Up " + growth + "% vs Yesterday")
                    .detail("Today's revenue $" + ctx.getTodayRevenue()
                            + " is higher than yesterday's $"
                            + ctx.getYesterdayRevenue() + ". Keep it up!")
                    .changePercent(growth).build());
        } else if (growth.compareTo(BigDecimal.ZERO) < 0) {
            insights.add(InsightsDto.Insight.builder()
                    .type("NEGATIVE").icon("📉")
                    .title("Revenue Down " + growth.abs() + "% vs Yesterday")
                    .detail("Today's revenue $" + ctx.getTodayRevenue()
                            + " dropped from yesterday's $"
                            + ctx.getYesterdayRevenue() + ".")
                    .changePercent(growth).build());
        } else {
            insights.add(InsightsDto.Insight.builder()
                    .type("NEUTRAL").icon("➡️")
                    .title("Revenue Steady vs Yesterday")
                    .detail("Today's revenue $" + ctx.getTodayRevenue()
                            + " matches yesterday.")
                    .build());
        }

        // ── Top product ───────────────────────────────────────────────────────
        if (ctx.getTopProductsToday() != null
                && !ctx.getTopProductsToday().isEmpty()) {
            InsightsDto.ProductStat top = ctx.getTopProductsToday().get(0);
            insights.add(InsightsDto.Insight.builder()
                    .type("POSITIVE").icon("🏆")
                    .title("Best Seller: " + top.getName())
                    .detail(top.getName() + " sold " + top.getQuantitySold()
                            + " units today earning $" + top.getRevenue()
                            + ". Stock left: " + top.getCurrentStock() + ".")
                    .build());
            if (top.getCurrentStock() <= 10)
                alerts.add("⚠️ Best seller '" + top.getName()
                        + "' only " + top.getCurrentStock()
                        + " units left — restock now!");
        } else {
            insights.add(InsightsDto.Insight.builder()
                    .type("NEUTRAL").icon("📦")
                    .title("No Sales Yet Today")
                    .detail("No transactions today. Month total: $"
                            + ctx.getMonthRevenue() + ".")
                    .build());
        }

        // ── Profit ────────────────────────────────────────────────────────────
        insights.add(InsightsDto.Insight.builder()
                .type("POSITIVE").icon("💰")
                .title("Today's Profit: $" + ctx.getTodayProfit())
                .detail("You made $" + ctx.getTodayProfit()
                        + " profit from " + ctx.getTodayTransactions()
                        + " sales. Month profit: $"
                        + ctx.getMonthProfit() + ".")
                .build());

        // ── Profit margin ─────────────────────────────────────────────────────
        boolean good = ctx.getProfitMargin()
                .compareTo(BigDecimal.valueOf(30)) >= 0;
        boolean ok   = ctx.getProfitMargin()
                .compareTo(BigDecimal.valueOf(15)) >= 0;
        insights.add(InsightsDto.Insight.builder()
                .type(good ? "POSITIVE" : ok ? "NEUTRAL" : "NEGATIVE")
                .icon("📊")
                .title("Profit Margin: " + ctx.getProfitMargin() + "%")
                .detail("Running at " + ctx.getProfitMargin()
                        + "% margin this month. "
                        + (good ? "Excellent — above 30% target!"
                        :  ok   ? "Good — aim for above 30%."
                        :         "Below target — review costs."))
                .build());

        // ── Inventory alerts ──────────────────────────────────────────────────
        if (ctx.getOutOfStockCount() > 0)
            alerts.add("🚨 " + ctx.getOutOfStockCount()
                    + " products OUT OF STOCK — order immediately!");
        if (ctx.getCriticalReorders() > 0)
            alerts.add("⚠️ " + ctx.getCriticalReorders()
                    + " products need reorder within 3 days!");
        if (ctx.getLowStockCount() > 0)
            alerts.add("📦 " + ctx.getLowStockCount()
                    + " products running low on stock.");
        if (ctx.getTodayRefunds()
                .compareTo(BigDecimal.valueOf(50)) > 0)
            alerts.add("🔄 High refunds today: $"
                    + ctx.getTodayRefunds()
                    + " — check product quality.");

        // ── Recommendations ───────────────────────────────────────────────────
        if (ctx.getSlowMovingCount() > 0)
            recommendations.add("Discount " + ctx.getSlowMovingCount()
                    + " slow-moving products to clear stock.");
        if (ctx.getOutOfStockCount() > 0 || ctx.getCriticalReorders() > 0)
            recommendations.add(
                    "Place stock orders today for critical items.");
        if (ctx.getRevenueGrowthVsLastMonth()
                .compareTo(BigDecimal.ZERO) < 0)
            recommendations.add("Revenue below last month — "
                    + "run a promotion to boost sales.");
        else
            recommendations.add("Good growth! Expand top-selling "
                    + "product lines to maximise revenue.");
        if (!"N/A".equals(ctx.getPeakHourToday()))
            recommendations.add("Ensure full staffing at "
                    + ctx.getPeakHourToday()
                    + " — your busiest hour today.");

        // ── Summary ───────────────────────────────────────────────────────────
        String trend = growth.compareTo(BigDecimal.ZERO) >= 0
                ? "up " + growth + "%"
                : "down " + growth.abs() + "%";
        String summary = ctx.getTodayTransactions() == 0
                ? "No sales today yet. Month total: $"
                  + ctx.getMonthRevenue() + "."
                : "Revenue " + trend + " vs yesterday — $"
                  + ctx.getTodayRevenue() + " from "
                  + ctx.getTodayTransactions()
                  + " sales. Margin: " + ctx.getProfitMargin() + "%.";

        return InsightsDto.InsightResponse.builder()
                .summary(summary)
                .insights(insights)
                .alerts(alerts)
                .recommendations(recommendations)
                .rawData(ctx)
                .provider("Rule-Based Engine")
                .generatedAt(LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }
}
