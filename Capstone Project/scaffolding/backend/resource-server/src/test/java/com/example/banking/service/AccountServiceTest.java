package com.example.banking.service;

import com.example.banking.dto.AccountDto;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.model.AccountEntity;
import com.example.banking.model.AccountType;
import com.example.banking.repository.AccountRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountService svc = new AccountService(accounts);

    private AccountEntity account(String id, String owner) {
        return new AccountEntity(id, owner, AccountType.CHECKING, "USD",
                new BigDecimal("100.00"), LocalDateTime.now());
    }

    // ------------------------------------------------------------------ listForOwner

    @Test
    void listForOwner_returns_all_owned_accounts() {
        AccountEntity a1 = account("acc_1", "usr_1");
        AccountEntity a2 = account("acc_2", "usr_1");
        when(accounts.findByOwnerId("usr_1")).thenReturn(List.of(a1, a2));

        List<AccountDto> result = svc.listForOwner("usr_1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AccountDto::accountId)
                .containsExactlyInAnyOrder("acc_1", "acc_2");
    }

    @Test
    void listForOwner_returns_empty_list_when_no_accounts() {
        when(accounts.findByOwnerId("usr_new")).thenReturn(List.of());

        List<AccountDto> result = svc.listForOwner("usr_new");

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------ findOwnedAccount

    @Test
    void findOwnedAccount_returns_dto_for_owned_account() {
        AccountEntity acct = account("acc_1", "usr_1");
        when(accounts.findById("acc_1")).thenReturn(Optional.of(acct));

        AccountDto result = svc.findOwnedAccount("acc_1", "usr_1");

        assertThat(result.accountId()).isEqualTo("acc_1");
    }

    @Test
    void findOwnedAccount_throws_not_found_for_non_existent_account() {
        when(accounts.findById("acc_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.findOwnedAccount("acc_missing", "usr_1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findOwnedAccount_throws_not_found_when_account_belongs_to_another_user() {
        // Ownership check returns 404, not 403, to avoid leaking account existence
        AccountEntity acct = account("acc_other", "usr_other");
        when(accounts.findById("acc_other")).thenReturn(Optional.of(acct));

        assertThatThrownBy(() -> svc.findOwnedAccount("acc_other", "usr_attacker"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("acc_other");
    }
}
