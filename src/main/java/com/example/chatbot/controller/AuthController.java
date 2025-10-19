package com.example.chatbot.controller;

import com.example.chatbot.dto.RegisterRequest;
import com.example.chatbot.dto.RegisterResponse;
import com.example.chatbot.model.User;
import com.example.chatbot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    @Autowired
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = userService.createUser(request.getEmail());
            
            RegisterResponse response = new RegisterResponse(
                    user.getId(),
                    user.getApiKey(),
                    user.getEmail(),
                    "Registration successful. Save your API key securely."
            );
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
} 