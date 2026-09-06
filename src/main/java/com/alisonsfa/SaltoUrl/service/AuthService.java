package com.alisonsfa.SaltoUrl.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.alisonsfa.SaltoUrl.config.security.JwtService;
import com.alisonsfa.SaltoUrl.domain.entity.User;
import com.alisonsfa.SaltoUrl.domain.enums.Role;
import com.alisonsfa.SaltoUrl.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Service 
public class AuthService {
    
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
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

    public User login(String email, String rawPassword) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, rawPassword)
        );

        User user = (User) authentication.getPrincipal();
        return user;
    }
    



}
