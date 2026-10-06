package com.lcsalvess.bankingsystem.dto.response;

import com.lcsalvess.bankingsystem.entity.Client;

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
                client.getCpf(),
                client.getEmail(),
                client.getPhoneNumber()
        );
    }
}
