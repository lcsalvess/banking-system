package com.lucas.sistemabancario.controller;

import com.lucas.sistemabancario.dto.request.transaction.AccountOperationRequestDTO;
import com.lucas.sistemabancario.dto.request.transaction.TransferRequestDTO;
import com.lucas.sistemabancario.dto.response.TransactionResponseDTO;
import com.lucas.sistemabancario.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponseDTO deposit(@Valid @RequestBody AccountOperationRequestDTO dto) {
        return transactionService.deposit(dto);
    }

    @GetMapping("/accounts/{accountNumber}")
    public List<TransactionResponseDTO> findByAccountNumber(@PathVariable String accountNumber, @RequestParam String digit) {
        return transactionService.findByAccountNumber(accountNumber, digit);
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponseDTO withdraw(@Valid @RequestBody AccountOperationRequestDTO dto) {
        return transactionService.withdraw(dto);
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponseDTO transfer(@Valid @RequestBody TransferRequestDTO dto) {
        return transactionService.transfer(dto);
    }

    @PostMapping("/yield/{accountNumber}")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponseDTO applyYield(@PathVariable String accountNumber, @RequestParam String digit) {
        return transactionService.applyYield(accountNumber, digit);
    }
}
