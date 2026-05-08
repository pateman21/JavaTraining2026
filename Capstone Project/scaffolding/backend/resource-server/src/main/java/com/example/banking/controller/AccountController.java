package com.example.banking.controller;

import com.example.banking.dto.AccountDto;
import com.example.banking.dto.TransactionDto;
import com.example.banking.service.AccountService;
import com.example.banking.service.TransactionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    public AccountController(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    /** Caller's own accounts only. */
    @GetMapping
    public List<AccountDto> listMine(Authentication auth) {
        return accountService.listForOwner(auth.getName());
    }

    /** Single account, owned by caller. 404 if not exists OR not owned. */
    @GetMapping("/{accountId}")
    public AccountDto getOne(@PathVariable String accountId, Authentication auth) {
        return accountService.findOwnedAccount(accountId, auth.getName());
    }

    /** Transactions for an owned account, newest first. */
    @GetMapping("/{accountId}/transactions")
    public List<TransactionDto> getTransactions(@PathVariable String accountId,
                                                Authentication auth) {
        return transactionService.listForOwnedAccount(accountId, auth.getName());
    }
}
