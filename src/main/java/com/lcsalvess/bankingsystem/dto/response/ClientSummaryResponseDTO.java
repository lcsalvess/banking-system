package com.lcsalvess.bankingsystem.dto.response;

import com.lcsalvess.bankingsystem.entity.Client;
import com.lcsalvess.bankingsystem.util.CpfMasker;

public record ClientSummaryResponseDTO(
        Long id,
        String name,
        String cpf,
        String email,
        String phoneNumber
) {
    public static ClientSummaryResponseDTO fromEntity(Client client) {
        return new ClientSummaryResponseDTO(
                client.getId(),
                client.getName(),
                CpfMasker.mask(client.getCpf()),
                client.getEmail(),
                client.getPhoneNumber()
        );
    }
}
