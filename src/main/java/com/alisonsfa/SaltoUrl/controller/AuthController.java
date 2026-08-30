package com.alisonsfa.SaltoUrl.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.alisonsfa.SaltoUrl.dto.AuthResponse;
import com.alisonsfa.SaltoUrl.dto.RegisterRequest;
import com.alisonsfa.SaltoUrl.service.AuthService;

import jakarta.validation.Valid;

@RestController 
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody @Valid RegisterRequest request) {
        boolean success = authService.register(request.email(), request.password());
        
        if (!success) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email já cadastrado");
        }
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@RequestBody @Valid RegisterRequest request) {
        return authService.login(request.email(), request.password())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
