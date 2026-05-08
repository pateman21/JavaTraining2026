package com.example.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for POST /api/v1/transactions.
 *
 * Field-level validation lives here. Cross-field rules (e.g., counterparty
 * required only for TRANSFER_OUT, balance >= amount) belong in
 * TransactionService and surface as BusinessRuleException / InsufficientFundsException.
 */
public record NewTransactionRequest(

        @NotBlank
        String accountId,

        @NotBlank
        String type,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        @Digits(integer = 15, fraction = 4, message = "amount must have at most 4 decimal places")
        BigDecimal amount,

        String counterparty,

        @Size(max = 255)
        String description

) { }
