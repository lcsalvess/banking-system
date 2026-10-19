package com.lcsalvess.bankingsystem.entity.account;

import com.lcsalvess.bankingsystem.entity.client.Client;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "checking_accounts")
@PrimaryKeyJoinColumn(
        name = "id",
        foreignKey = @ForeignKey(name = "fk_checking_accounts_account")
)
public class CheckingAccount extends Account {
    public CheckingAccount() {
    }

    public CheckingAccount(Client client, String accountNumber, String accountDigit) {
        super(client, accountNumber, accountDigit, AccountType.CHECKING);
    }
}
