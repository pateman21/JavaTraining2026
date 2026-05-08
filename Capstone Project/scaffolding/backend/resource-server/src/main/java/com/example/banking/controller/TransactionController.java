package com.example.banking.controller;

import com.example.banking.dto.NewTransactionRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.kafka.TransactionEvent;
import com.example.banking.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/{transactionId}")
    public TransactionDto getOne(@PathVariable String transactionId, Authentication auth) {
        return transactions.findOwnedTransaction(transactionId, auth.getName());
    }

    /**
     * Submit a transaction. Returns 201 with the created row(s).
     *
     * Two important rules from the spec:
     *   1. The Kafka publish happens AFTER the @Transactional in the
     *      service has committed — that's why we publish here in the
     *      controller, not inside the service.
     *   2. A TRANSFER_OUT between the caller's own accounts produces TWO
     *      rows; everything else returns one. The Location header points
     *      at the FIRST row.
     */
    @PostMapping
    public ResponseEntity<List<TransactionDto>> create(@Valid @RequestBody NewTransactionRequest req,
                                                       Authentication auth) {
        String callerUserId = auth.getName();
        List<TransactionDto> created = transactions.submit(req, callerUserId);

        // Publish one event per row. Currency is USD for the capstone.
        for (TransactionDto row : created) {
            TransactionEvent event = transactions.toEvent(row, callerUserId, "USD");
            transactions.publishEvent(event);
        }

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(created.get(0).transactionId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
