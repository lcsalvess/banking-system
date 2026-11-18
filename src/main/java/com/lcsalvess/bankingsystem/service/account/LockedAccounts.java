package com.lcsalvess.bankingsystem.service.account;

import com.lcsalvess.bankingsystem.entity.account.Account;

/**
 * Represents a pair of accounts locked for a financial transfer.
 *
 * @param fromAccount the source account
 * @param toAccount the destination account
 */
public record LockedAccounts(
    Account fromAccount,
    Account toAccount
) {
}
