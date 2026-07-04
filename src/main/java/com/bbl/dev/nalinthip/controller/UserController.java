package com.bbl.dev.nalinthip.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bbl.dev.nalinthip.dto.request.UserRequest;
import com.bbl.dev.nalinthip.dto.response.UserResponse;
import com.bbl.dev.nalinthip.service.UserService;

import jakarta.validation.Valid;


@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public List<UserResponse> getUsers() {
        return userService.getUsers();
    }

    @GetMapping("/users/{userId}")
    public UserResponse getUserById(@PathVariable String userId) {
        return userService.getUserById(userId);
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest userRequest) {
        UserResponse created = userService.createUser(userRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);   
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable String userId,
                                                   @Valid @RequestBody UserRequest userRequest) {
        UserResponse updated = userService.updateUser(userId, userRequest);
        return updated != null
                ? ResponseEntity.ok(updated)           
                : ResponseEntity.notFound().build();      
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        boolean deleted = userService.deleteUser(userId);
        return deleted
                ? ResponseEntity.noContent().build()      
                : ResponseEntity.notFound().build();     
    }
}