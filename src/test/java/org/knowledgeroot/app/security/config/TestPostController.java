package org.knowledgeroot.app.security.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.boot.test.context.TestComponent
class TestPostController {
    @PostMapping("/dummy")
    ResponseEntity<String> post() {
        return ResponseEntity.ok("ok");
    }
}
