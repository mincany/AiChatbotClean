package com.example.chatbot.service;

import com.example.chatbot.model.User;
import com.example.chatbot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String email) {
        // Check if user already exists
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            throw new RuntimeException("User with email " + email + " already exists");
        }

        // Generate unique IDs
        String userId = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String apiKey = "ak_" + UUID.randomUUID().toString().replace("-", "");

        // Create and save user
        User user = new User(userId, apiKey, email);
        userRepository.save(user);

        return user;
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByApiKey(String apiKey) {
        return userRepository.findByApiKey(apiKey);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public String getUserIdFromApiKey(String apiKey) {
        Optional<User> user = findByApiKey(apiKey);
        return user.map(User::getUserId).orElse(null);
    }
} 