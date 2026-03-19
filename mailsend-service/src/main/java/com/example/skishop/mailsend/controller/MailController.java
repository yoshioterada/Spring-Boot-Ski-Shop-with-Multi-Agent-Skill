package com.example.skishop.mailsend.controller;

import com.example.skishop.mailsend.dto.MailLogResponse;
import com.example.skishop.mailsend.dto.MailStatsResponse;
import com.example.skishop.mailsend.dto.TestMailRequest;
import com.example.skishop.mailsend.service.MailService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mail")
public class MailController {

    private final MailService mailService;

    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<MailLogResponse>> listLogs(Pageable pageable) {
        return ResponseEntity.ok(mailService.list(pageable));
    }

    @GetMapping("/logs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MailLogResponse> getLog(@PathVariable UUID id) {
        return ResponseEntity.ok(mailService.get(id));
    }

    @PostMapping("/logs/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MailLogResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(mailService.retry(id));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<MailStatsResponse> stats() {
        return ResponseEntity.ok(mailService.stats());
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MailLogResponse> testMail(@Valid @RequestBody TestMailRequest request) {
        return ResponseEntity.ok(mailService.sendTestMail(request));
    }
}
