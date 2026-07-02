package com.fr.ai.debugagent.controller;

import com.fr.ai.debugagent.oms.OmsCookieStore;
import com.fr.ai.debugagent.oms.OmsLoginRequest;
import com.fr.ai.debugagent.oms.OmsLoginResult;
import com.fr.ai.debugagent.oms.OmsLoginService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/oms")
public class OmsController {

    private final OmsLoginService loginService;
    private final OmsCookieStore cookieStore;

    public OmsController(OmsLoginService loginService, OmsCookieStore cookieStore) {
        this.loginService = loginService;
        this.cookieStore = cookieStore;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OmsLoginResult> login(@RequestBody OmsLoginRequest request) {
        return ResponseEntity.ok(loginService.login(request.environment()));
    }

    @GetMapping(value = "/cookies/{environment}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cookieStatus(@PathVariable("environment") String environment) {
        return cookieStore.get(environment)
                .<ResponseEntity<?>>map(session -> ResponseEntity.ok(Map.of(
                        "environment", session.environment(),
                        "ssoHost", session.ssoHost(),
                        "omsHost", session.omsHost(),
                        "cookieNames", session.cookieNames(),
                        "maskedCookieHeader", session.maskedCookieHeader(),
                        "createdAt", session.createdAt())))
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "environment", environment,
                        "cached", false,
                        "message", "当前环境没有已缓存的 OMS Cookie")));
    }
}
