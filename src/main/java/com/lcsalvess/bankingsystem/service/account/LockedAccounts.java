package com.lcsalvess.bankingsystem.service.account;

import com.lcsalvess.bankingsystem.entity.account.Account;

public record LockedAccounts(
        Account fromAccount,
        Account toAccount
) {
}
