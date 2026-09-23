package com.lucas.bankingsystem.controller;

import com.lucas.bankingsystem.dto.request.AccountRequestDTO;
import com.lucas.bankingsystem.dto.response.AccountResponseDTO;
import com.lucas.bankingsystem.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponseDTO> findAll() {
        return accountService.findAll();
    }

    @GetMapping("/{accountNumber}")
    public AccountResponseDTO findByAccountNumber(@PathVariable String accountNumber,
                                                  @RequestParam String digit) {
        return accountService.findByAccountNumber(accountNumber, digit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponseDTO create(@Valid @RequestBody AccountRequestDTO dto) {
        return accountService.create(dto);
    }

    @PatchMapping("/{accountNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String accountNumber,
                       @RequestParam String digit) {
        accountService.cancel(accountNumber, digit);
    }
}
