package com.example.banking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "BANK_USERS")
public class BankUserEntity {

    @Id
    @Column(name = "USER_ID", length = 64, nullable = false)
    private String userId;

    @Column(name = "SUBJECT", length = 255, nullable = false, unique = true)
    private String subject;

    @Column(name = "EMAIL", length = 255, nullable = false)
    private String email;

    @Column(name = "DISPLAY_NAME", length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", length = 16, nullable = false)
    private UserRole role;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    protected BankUserEntity() { }

    private BankUserEntity(String userId, String subject, String email,
                           String displayName, UserRole role, LocalDateTime createdAt) {
        this.userId = userId;
        this.subject = subject;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = createdAt;
    }

    /** Factory for the first-login path. New users default to CUSTOMER. */
    public static BankUserEntity newCustomer(String subject, String email, String displayName) {
        return newUser(subject, email, displayName, UserRole.CUSTOMER);
    }

    /** Factory for first-login with a specific role (e.g. ADMIN from JWT claim). */
    public static BankUserEntity newUser(String subject, String email, String displayName, UserRole role) {
        return new BankUserEntity(
                "usr_" + UUID.randomUUID(),
                subject,
                email,
                displayName,
                role,
                LocalDateTime.now()
        );
    }

    public String getUserId()       { return userId; }
    public String getSubject()      { return subject; }
    public String getEmail()        { return email; }
    public String getDisplayName()  { return displayName; }
    public UserRole getRole()       { return role; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
}
