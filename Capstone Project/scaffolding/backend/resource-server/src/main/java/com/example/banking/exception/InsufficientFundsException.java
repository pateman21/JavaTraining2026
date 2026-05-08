package com.example.banking.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountId, BigDecimal balance, BigDecimal attempted) {
        super("Account " + accountId + " has balance " + balance
                + ", transaction of " + attempted + " would overdraw");
    }
}
