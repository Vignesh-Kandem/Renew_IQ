package com.renewiq.service;
import com.renewiq.model.AppUser;
import com.renewiq.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class CurrentUser {
    private final UserRepository users;
    public CurrentUser(UserRepository users) { this.users = users; }
    public AppUser get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw unauthorized();
        return users.findByEmail(auth.getName()).filter(AppUser::isEnabled).orElseThrow(this::unauthorized);
    }
    public Long id() { return get().getId(); }
    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
    }
}
