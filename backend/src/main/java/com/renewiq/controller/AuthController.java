package com.renewiq.controller;

import com.renewiq.model.AppUser;
import com.renewiq.repository.UserRepository;
import com.renewiq.service.CurrentUser;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrfTokens;
    private final CurrentUser current;
    public AuthController(UserRepository users, PasswordEncoder passwords, AuthenticationManager authentication,
                          SecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrfTokens, CurrentUser current) {
        this.users = users; this.passwords = passwords; this.authentication = authentication;
        this.contexts = contexts; this.csrfTokens = csrfTokens; this.current = current;
    }
    public record Signup(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=254) String email,
                         @NotBlank @Size(min=8, max=72) String password) { }
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) { }
    public record UserView(Long id, String name, String email) {
        static UserView of(AppUser u) { return new UserView(u.getId(), u.getName(), u.getEmail()); }
    }
    @GetMapping("/csrf") public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }
    @GetMapping("/me") public UserView me() { return UserView.of(current.get()); }
    @PostMapping("/signup") @ResponseStatus(HttpStatus.CREATED)
    public UserView signup(@Valid @RequestBody Signup in, HttpServletRequest req, HttpServletResponse res) {
        checkPasswordBytes(in.password());
        String email = normalize(in.email());
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        var user = new AppUser();
        user.setName(in.name().trim()); user.setEmail(email);
        user.setPasswordHash(passwords.encode(in.password())); user.setEnabled(true);
        try { users.saveAndFlush(user); }
        catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        return signIn(email, in.password(), req, res);
    }
    @PostMapping("/login")
    public UserView login(@Valid @RequestBody Login in, HttpServletRequest req, HttpServletResponse res) {
        checkPasswordBytes(in.password());
        return signIn(normalize(in.email()), in.password(), req, res);
    }
    private UserView signIn(String email, String password, HttpServletRequest req, HttpServletResponse res) {
        try {
            var auth = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            if (req.getSession(false) != null) req.changeSessionId();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth); SecurityContextHolder.setContext(context);
            contexts.saveContext(context, req, res);
            csrfTokens.saveToken(null, req, res);
            return UserView.of(users.findByEmail(email).orElseThrow());
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
    }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private void checkPasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes.");
    }
}
