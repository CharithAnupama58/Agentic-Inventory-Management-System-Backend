package com.pos.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.system.dto.PredictionDto;
import com.pos.system.model.Product;
import com.pos.system.repository.ProductRepository;
import com.pos.system.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryPredictionService {

    private final PredictionEngineService predictionEngineService;
    private final ProductRepository       productRepository;
    private final ObjectMapper            objectMapper;

    @Value("${ai.groq.api.key:}")
    private String groqKey;

    @Value("${ai.groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    // ── Generate full prediction report ──────────────────────────────────────
    public PredictionDto.PredictionResponse generatePredictions() {
        UUID tenantId = TenantContext.getTenantId();

        log.info("Generating inventory predictions for tenant: {}", tenantId);

        List<PredictionDto.ProductPrediction> allPredictions =
                predictionEngineService.buildAllPredictions();

        List<Product> products =
                productRepository.findAllByTenantId(tenantId);

        // ── Categorize by risk ────────────────────────────────────────────────
        List<PredictionDto.ProductPrediction> critical = allPredictions.stream()
                .filter(p -> p.getRiskLevel().equals("CRITICAL"))
                .collect(Collectors.toList());

        List<PredictionDto.ProductPrediction> high = allPredictions.stream()
                .filter(p -> p.getRiskLevel().equals("HIGH"))
                .collect(Collectors.toList());

        List<PredictionDto.ProductPrediction> medium = allPredictions.stream()
                .filter(p -> p.getRiskLevel().equals("MEDIUM"))
                .collect(Collectors.toList());

        List<PredictionDto.ProductPrediction> safe = allPredictions.stream()
                .filter(p -> p.getRiskLevel().equals("LOW")
                          || p.getRiskLevel().equals("SAFE"))
                .collect(Collectors.toList());

        // ── Build summary ─────────────────────────────────────────────────────
        PredictionDto.PredictionSummary summary =
                predictionEngineService.buildSummary(allPredictions, products);

        // ── Get AI analysis ───────────────────────────────────────────────────
        String aiAnalysis = getAIAnalysis(critical, high, medium, summary);

        return PredictionDto.PredictionResponse.builder()
                .criticalProducts(critical)
                .highRiskProducts(high)
                .mediumRiskProducts(medium)
                .safeProducts(safe)
                .allPredictions(allPredictions)
                .summary(summary)
                .aiAnalysis(aiAnalysis)
                .generatedAt(LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }

    // ── Get single product prediction ─────────────────────────────────────────
    public PredictionDto.ProductPrediction getProductPrediction(UUID productId) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository
                .findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new RuntimeException(
                        "Product not found: " + productId));
        return predictionEngineService
                .buildProductPrediction(product, tenantId);
    }

    // ── Call Groq for natural language analysis ───────────────────────────────
    private String getAIAnalysis(
            List<PredictionDto.ProductPrediction> critical,
            List<PredictionDto.ProductPrediction> high,
            List<PredictionDto.ProductPrediction> medium,
            PredictionDto.PredictionSummary summary) {

        if (groqKey == null || groqKey.isBlank()
                || groqKey.equals("gsk_PASTE_YOUR_GROQ_KEY_HERE")) {
            return buildRuleBasedAnalysis(critical, high, summary);
        }

        try {
            return callGroqForAnalysis(critical, high, medium, summary);
        } catch (Exception e) {
            log.error("Groq analysis failed: {} — using rule-based",
                    e.getMessage());
            return buildRuleBasedAnalysis(critical, high, summary);
        }
    }

    private String callGroqForAnalysis(
            List<PredictionDto.ProductPrediction> critical,
            List<PredictionDto.ProductPrediction> high,
            List<PredictionDto.ProductPrediction> medium,
            PredictionDto.PredictionSummary summary) throws Exception {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders  headers      = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqKey);

        String prompt = buildAnalysisPrompt(critical, high, medium, summary);

        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role",    "system");
        sysMsg.put("content",
                "You are an inventory management expert. "
                + "Give concise, actionable advice in 2-3 sentences. "
                + "Be specific with product names and numbers.");

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role",    "user");
        userMsg.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model",       "llama-3.3-70b-versatile");
        body.put("messages",    List.of(sysMsg, userMsg));
        body.put("max_tokens",  300);
        body.put("temperature", 0.4);
        body.put("stream",      false);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(body, headers);

        log.info("Calling Groq for inventory analysis...");
        ResponseEntity<String> response =
                restTemplate.postForEntity(groqUrl, entity, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("choices").get(0)
                   .path("message").path("content").asText();
    }

    private String buildAnalysisPrompt(
            List<PredictionDto.ProductPrediction> critical,
            List<PredictionDto.ProductPrediction> high,
            List<PredictionDto.ProductPrediction> medium,
            PredictionDto.PredictionSummary summary) {

        StringBuilder sb = new StringBuilder();
        sb.append("Inventory status for a retail store:\n\n");

        sb.append("SUMMARY:\n");
        sb.append("- Total products: ")
          .append(summary.getTotalProductsAnalyzed()).append("\n");
        sb.append("- Critical (stockout imminent): ")
          .append(summary.getCriticalCount()).append("\n");
        sb.append("- High risk (< 7 days): ")
          .append(summary.getHighRiskCount()).append("\n");
        sb.append("- Medium risk (7-14 days): ")
          .append(summary.getMediumRiskCount()).append("\n");
        sb.append("- Estimated reorder cost: $")
          .append(summary.getTotalReorderCost()).append("\n\n");

        if (!critical.isEmpty()) {
            sb.append("CRITICAL PRODUCTS:\n");
            critical.stream().limit(5).forEach(p ->
                sb.append("- ").append(p.getProductName())
                  .append(": ").append(p.getCurrentStock()).append(" left, ")
                  .append(p.getPrediction()).append("\n"));
            sb.append("\n");
        }

        if (!high.isEmpty()) {
            sb.append("HIGH RISK PRODUCTS:\n");
            high.stream().limit(5).forEach(p ->
                sb.append("- ").append(p.getProductName())
                  .append(": ").append(p.getDaysUntilStockout())
                  .append(" days left\n"));
            sb.append("\n");
        }

        sb.append("Give a 2-3 sentence actionable summary for the store owner. "
                + "Be direct and specific.");

        return sb.toString();
    }

    // ── Rule-based analysis fallback ──────────────────────────────────────────
    private String buildRuleBasedAnalysis(
            List<PredictionDto.ProductPrediction> critical,
            List<PredictionDto.ProductPrediction> high,
            PredictionDto.PredictionSummary summary) {

        if (summary.getCriticalCount() == 0 && summary.getHighRiskCount() == 0)
            return "✅ Your inventory looks healthy! All products have "
                    + "sufficient stock. Continue monitoring daily.";

        StringBuilder sb = new StringBuilder();

        if (!critical.isEmpty()) {
            sb.append("🚨 URGENT: ");
            if (critical.size() == 1) {
                sb.append(critical.get(0).getProductName())
                  .append(" needs immediate reorder — ")
                  .append(critical.get(0).getPrediction()).append(". ");
            } else {
                sb.append(critical.size())
                  .append(" products need emergency reorder including ")
                  .append(critical.get(0).getProductName()).append(". ");
            }
        }

        if (!high.isEmpty()) {
            sb.append("⚠️ ")
              .append(high.size())
              .append(" product(s) will run out within 7 days — ");
            sb.append("place orders now to avoid stockouts. ");
        }

        sb.append("Estimated reorder cost: $")
          .append(summary.getTotalReorderCost()).append(".");

        return sb.toString();
    }
}
