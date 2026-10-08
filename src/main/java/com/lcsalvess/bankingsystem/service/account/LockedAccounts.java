package com.lcsalvess.bankingsystem.service.account;

import com.lcsalvess.bankingsystem.entity.Account;

public record LockedAccounts(
        Account fromAccount,
        Account toAccount
) {
}
