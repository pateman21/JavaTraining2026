package com.example.banking.security;

import com.example.banking.model.BankUserEntity;
import com.example.banking.model.UserRole;
import com.example.banking.repository.BankUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class JwtAuthConverterTest {

    private final BankUserRepository users = mock(BankUserRepository.class);
    private final JwtAuthConverter converter = new JwtAuthConverter(users);

    private Jwt jwt(String subject, String role, String email) {
        return Jwt.withTokenValue("test.jwt.token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("role", role)
                .claim("email", email)
                .claim("name", "Test User")
                .build();
    }

    // ------------------------------------------------------------------ first login creates row

    @Test
    void first_login_creates_bank_user_row_with_role_from_claim() {
        /*
         * TODO (Chapter 06 Part 2 — Step 3): Verify that the first call to convert()
         * creates a new BANK_USERS row using the JWT sub, email, and role claims.
         *
         * Setup:
         *   when(users.findBySubject("alice-sub")).thenReturn(Optional.empty());
         *   when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
         *
         * Exercise:
         *   converter.convert(jwt("alice-sub", "CUSTOMER", "alice@test.com"));
         *
         * Verify (using argThat):
         *   verify(users).save(argThat(u ->
         *       u.getSubject().equals("alice-sub") &&
         *       u.getEmail().equals("alice@test.com") &&
         *       u.getRole() == UserRole.CUSTOMER
         *   ));
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }

    // ------------------------------------------------------------------ subsequent login reuses existing row

    @Test
    void subsequent_login_reuses_existing_row_and_role() {
        /*
         * TODO (Chapter 06 Part 2 — Step 4): Verify that a repeat login does NOT
         * call users.save() — the existing row is reused.
         *
         * Setup:
         *   BankUserEntity existing = BankUserEntity.newUser(
         *       "alice-sub", "alice@test.com", "Alice", UserRole.CUSTOMER);
         *   when(users.findBySubject("alice-sub")).thenReturn(Optional.of(existing));
         *
         * Exercise:
         *   converter.convert(jwt("alice-sub", "CUSTOMER", "alice@test.com"));
         *
         * Verify:
         *   verify(users, never()).save(any());
         */
        // TODO: implement this test
        throw new UnsupportedOperationException("test not yet implemented");
    }

    // ------------------------------------------------------------------ admin role mapping

    @Test
    void admin_role_claim_maps_to_role_admin_authority() {
        BankUserEntity adminUser = BankUserEntity.newUser(
                "admin-sub", "admin@mock.local", "Demo Admin", UserRole.ADMIN);
        when(users.findBySubject("admin-sub")).thenReturn(Optional.of(adminUser));

        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(
                jwt("admin-sub", "ADMIN", "admin@mock.local"));

        assertThat(token.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    // ------------------------------------------------------------------ customer role mapping

    @Test
    void customer_role_claim_maps_to_role_customer_authority() {
        BankUserEntity customer = BankUserEntity.newUser(
                "alice-sub", "alice@mock.local", "Alice", UserRole.CUSTOMER);
        when(users.findBySubject("alice-sub")).thenReturn(Optional.of(customer));

        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(
                jwt("alice-sub", "CUSTOMER", "alice@mock.local"));

        assertThat(token.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactly("ROLE_CUSTOMER");
    }

    // ------------------------------------------------------------------ null email fallback

    @Test
    void missing_email_claim_synthesizes_email_from_subject() {
        when(users.findBySubject("no-email-sub")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // JWT without email claim
        Jwt noEmail = Jwt.withTokenValue("test.jwt.token")
                .header("alg", "RS256")
                .subject("no-email-sub")
                .build();

        converter.convert(noEmail);

        verify(users).save(argThat(u ->
                u.getEmail().equals("no-email-sub@mock.local")
        ));
    }

    // ------------------------------------------------------------------ principal name is local userId

    @Test
    void principal_name_is_local_user_id_not_jwt_sub() {
        BankUserEntity existing = BankUserEntity.newUser(
                "alice-sub", "alice@test.com", "Alice", UserRole.CUSTOMER);
        when(users.findBySubject("alice-sub")).thenReturn(Optional.of(existing));

        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(
                jwt("alice-sub", "CUSTOMER", "alice@test.com"));

        assertThat(token.getName()).isEqualTo(existing.getUserId());
        assertThat(token.getName()).startsWith("usr_");
    }
}
