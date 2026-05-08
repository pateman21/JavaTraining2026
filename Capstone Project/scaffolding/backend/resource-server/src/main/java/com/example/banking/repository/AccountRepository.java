package com.example.banking.repository;

import com.example.banking.model.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {
    List<AccountEntity> findByOwnerId(String ownerId);
}
