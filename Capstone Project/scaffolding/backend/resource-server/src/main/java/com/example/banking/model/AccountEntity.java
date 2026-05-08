package com.example.banking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ACCOUNTS")
public class AccountEntity {

    @Id
    @Column(name = "ACCOUNT_ID", length = 64, nullable = false)
    private String accountId;

    @Column(name = "OWNER_ID", length = 64, nullable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACCOUNT_TYPE", length = 16, nullable = false)
    private AccountType accountType;

    @Column(name = "CURRENCY", length = 3, nullable = false)
    private String currency;

    @Column(name = "BALANCE", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    protected AccountEntity() { }

    public AccountEntity(String accountId, String ownerId, AccountType accountType,
                         String currency, BigDecimal balance, LocalDateTime createdAt) {
        this.accountId = accountId;
        this.ownerId = ownerId;
        this.accountType = accountType;
        this.currency = currency;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public String getAccountId()        { return accountId; }
    public String getOwnerId()          { return ownerId; }
    public AccountType getAccountType() { return accountType; }
    public String getCurrency()         { return currency; }
    public BigDecimal getBalance()      { return balance; }
    public LocalDateTime getCreatedAt()       { return createdAt; }

    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
