package com.example.banking.repository;

import com.example.banking.model.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
    List<TransactionEntity> findByAccountIdOrderByCreatedAtDesc(String accountId);
    List<TransactionEntity> findByTransferGroupId(String transferGroupId);
}
