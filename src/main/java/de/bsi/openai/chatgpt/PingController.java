package de.bsi.openai.chatgpt;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping
     public ResponseEntity<String> healthCheck(){
         return ResponseEntity.ok("Application is running");
     }
}
