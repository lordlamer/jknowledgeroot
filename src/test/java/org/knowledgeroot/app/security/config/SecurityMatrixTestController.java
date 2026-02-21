package org.knowledgeroot.app.security.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SecurityMatrixTestController {

    @GetMapping("/user/1")
    ResponseEntity<String> getUser() {
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/user")
    ResponseEntity<String> createUser() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/group/1")
    ResponseEntity<String> getGroup() {
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/group")
    ResponseEntity<String> createGroup() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/page/1")
    ResponseEntity<String> getPage() {
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/page")
    ResponseEntity<String> createPage() {
        return ResponseEntity.ok("ok");
    }

    @PutMapping("/page/1")
    ResponseEntity<String> updatePage() {
        return ResponseEntity.ok("ok");
    }

    @DeleteMapping("/page/1")
    ResponseEntity<String> deletePage() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/file/1")
    ResponseEntity<String> getFile() {
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/file")
    ResponseEntity<String> createFile() {
        return ResponseEntity.ok("ok");
    }

    @DeleteMapping("/file/1")
    ResponseEntity<String> deleteFile() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/download/1/test.txt")
    ResponseEntity<String> downloadAlias() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/api/demo")
    ResponseEntity<String> apiDemo() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/profile")
    ResponseEntity<String> profile() {
        return ResponseEntity.ok("ok");
    }
}
