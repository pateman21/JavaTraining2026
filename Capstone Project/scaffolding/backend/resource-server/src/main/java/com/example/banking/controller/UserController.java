package com.example.banking.controller;

import com.example.banking.dto.UserDto;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.repository.BankUserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final BankUserRepository users;

    public UserController(BankUserRepository users) {
        this.users = users;
    }

    /**
     * Returns the calling user's profile.
     *
     * JwtAuthConverter sets the principal name to the local userId.
     * authentication.getName() therefore returns the same userId that
     * account/transaction queries expect.
     *
     * The row always exists at this point: JwtAuthConverter creates
     * it on first login (upsert from the JWT's sub claim).
     */
    @GetMapping("/users/me")
    public UserDto me(Authentication auth) {
        return users.findById(auth.getName())
                .map(UserDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("user", auth.getName()));
    }

    /** ADMIN-only. URL filter denies non-admins; @PreAuthorize is a second layer. */
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> listAll() {
        return users.findAll().stream().map(UserDto::from).toList();
    }
}
