package com.lcsalvess.bankingsystem.service.client;

import com.lcsalvess.bankingsystem.service.address.AddressData;

import java.util.Objects;

/**
 * Dados já resolvidos para atualizar um cliente.
 * O endereço é opcional e, quando presente, sempre contém o resultado
 * da consulta de CEP, além do número e complemento informados.
 */
public record ClientUpdateData(
        String name,
        String email,
        String phoneNumber,
        AddressUpdateData address
) {

    public record AddressUpdateData(
            AddressData addressData,
            String streetNumber,
            String complement
    ) {
        public AddressUpdateData {
            Objects.requireNonNull(addressData, "addressData");
        }
    }
}