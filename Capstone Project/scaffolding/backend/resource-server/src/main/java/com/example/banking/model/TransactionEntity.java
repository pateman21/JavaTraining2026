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
@Table(name = "TRANSACTIONS")
public class TransactionEntity {

    @Id
    @Column(name = "TRANSACTION_ID", length = 64, nullable = false)
    private String transactionId;

    @Column(name = "ACCOUNT_ID", length = 64, nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 16, nullable = false)
    private TransactionType type;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 16, nullable = false)
    private TransactionStatus status;

    @Column(name = "COUNTERPARTY", length = 64)
    private String counterparty;

    @Column(name = "TRANSFER_GROUP_ID", length = 64)
    private String transferGroupId;

    @Column(name = "DESCRIPTION", length = 255)
    private String description;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    protected TransactionEntity() { }

    public TransactionEntity(String transactionId, String accountId, TransactionType type,
                             BigDecimal amount, TransactionStatus status, String counterparty,
                             String transferGroupId, String description, LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.status = status;
        this.counterparty = counterparty;
        this.transferGroupId = transferGroupId;
        this.description = description;
        this.createdAt = createdAt;
    }

    public String getTransactionId()    { return transactionId; }
    public String getAccountId()        { return accountId; }
    public TransactionType getType()    { return type; }
    public BigDecimal getAmount()       { return amount; }
    public TransactionStatus getStatus(){ return status; }
    public String getCounterparty()     { return counterparty; }
    public String getTransferGroupId()  { return transferGroupId; }
    public String getDescription()      { return description; }
    public LocalDateTime getCreatedAt()      { return createdAt; }

    public void setStatus(TransactionStatus status) { this.status = status; }
}
