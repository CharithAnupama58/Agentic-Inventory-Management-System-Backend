package com.pos.system.controller;

import com.pos.system.dto.ChatDto;
import com.pos.system.model.Campaign;
import com.pos.system.repository.CampaignRepository;
import com.pos.system.security.TenantContext;
import com.pos.system.service.BusinessChatAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final BusinessChatAgentService chatAgentService;
    private final CampaignRepository       campaignRepository;

    @PostMapping
    public ResponseEntity<ChatDto.ChatResponse> chat(
            @RequestBody ChatDto.ChatRequest request) {
        return ResponseEntity.ok(chatAgentService.chat(request));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<List<Campaign>> getCampaigns() {
        UUID tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(
                campaignRepository.findByTenantIdOrderByCreatedAtDesc(
                        tenantId));
    }
}
