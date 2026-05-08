package com.example.banking.repository;

import com.example.banking.model.BankUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankUserRepository extends JpaRepository<BankUserEntity, String> {
    Optional<BankUserEntity> findBySubject(String subject);
}
