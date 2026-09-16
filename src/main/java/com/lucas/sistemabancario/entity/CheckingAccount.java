package com.lucas.sistemabancario.entity;

import com.lucas.sistemabancario.entity.enums.AccountType;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "checking_account")
@PrimaryKeyJoinColumn(
        name = "id",
        foreignKey = @ForeignKey(name = "fk_checking_account_account")
)
public class CheckingAccount extends Account {
    public CheckingAccount() {
    }

    public CheckingAccount(Client client, String accountNumber, String accountDigit) {
        super(client, accountNumber, accountDigit, AccountType.CHECKING);
    }}
