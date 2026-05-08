package com.example.banking.dto;

import com.example.banking.model.AccountEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountDto(
        String accountId,
        String accountType,
        String currency,
        BigDecimal balance,
        LocalDateTime createdAt
) {
    public static AccountDto from(AccountEntity account) {
        return new AccountDto(
                account.getAccountId(),
                account.getAccountType().name(),
                account.getCurrency(),
                account.getBalance(),
                account.getCreatedAt()
        );
    }
}
