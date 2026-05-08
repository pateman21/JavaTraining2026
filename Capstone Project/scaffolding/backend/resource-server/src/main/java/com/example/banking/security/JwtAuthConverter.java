package com.example.banking.security;

import com.example.banking.model.BankUserEntity;
import com.example.banking.model.UserRole;
import com.example.banking.repository.BankUserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts a validated JWT into a Spring Security authentication.
 *
 * On first login, creates a BANK_USERS row from the JWT's sub claim.
 * On subsequent logins, reuses the existing row.
 *
 * The role is read from a custom "role" claim in the JWT (set by the
 * mock auth server's token customizer). If absent (e.g., Google tokens),
 * defaults to CUSTOMER.
 *
 * authentication.getName() returns the local userId (not the JWT sub).
 * This is the same value that all service methods use for ownership checks.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final BankUserRepository users;

    public JwtAuthConverter(BankUserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        /*
         * TODO (Day 2 — Step 3): Implement JWT → Spring Security principal conversion.
         *
         * 1. Extract the JWT subject: jwt.getSubject()
         *
         * 2. Extract email from the "email" claim (jwt.getClaimAsString("email")).
         *    If absent, synthesise one: subject + "@mock.local"
         *
         * 3. Extract display name from the "name" claim.
         *    If absent, fall back to subject.
         *
         * 4. Determine the role from the "role" claim:
         *      - If the claim equals "ADMIN" (case-insensitive) → UserRole.ADMIN
         *      - Otherwise → UserRole.CUSTOMER
         *
         * 5. Upsert the local BANK_USERS row:
         *      users.findBySubject(subject)
         *        .orElseGet(() -> users.save(BankUserEntity.newUser(subject, email, name, role)))
         *    This creates the row on first login and reuses it on every subsequent login.
         *
         * 6. Build the authorities list:
         *      List.of(new SimpleGrantedAuthority("ROLE_" + localUser.getRole().name()))
         *
         * 7. Return a JwtAuthenticationToken with:
         *      - the validated jwt
         *      - the authorities list
         *      - principal name = localUser.getUserId()   <—— NOT the JWT subject!
         *    This means Authentication.getName() returns the local userId everywhere.
         *
         * Security note: DO NOT use the JWT subject as the principal name directly.
         * The local userId is the primary key used for ALL ownership checks.
         */
        throw new UnsupportedOperationException("JwtAuthConverter.convert(): not yet implemented");
    }
}
