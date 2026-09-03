package com.alisonsfa.SaltoUrl.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.alisonsfa.SaltoUrl.config.security.JwtService;
import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.domain.enums.Role;
import com.alisonsfa.SaltoUrl.dto.AuthResponse;
import com.alisonsfa.SaltoUrl.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Service 
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public boolean register(String email, String rawPassword) {
        if(userRepository.findByEmail(email).isPresent()) {
            log.warn("Tentativa de registro com e-mail já existente: {}", email);
            return false;
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);

        userRepository.save(user);
        log.info("Novo usuário registrado com sucesso: {}", email);

        return true;
    }

    public Optional<AuthResponse> login(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .map(user -> {
                    String token = jwtService.generateToken(user);
                    log.debug("Login bem sucedido para o usuário: {}", email);
                    return new AuthResponse(token, "Bearer");
                });
    }
    



}
