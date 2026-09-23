package com.lucas.bankingsystem.dto.response;

import com.lucas.bankingsystem.entity.Client;

public record ClientResponseDTO(
        Long id,
        String name,
        String cpf,
        String email,
        String phoneNumber
) {
    public static ClientResponseDTO fromEntity(Client client) {
        return new ClientResponseDTO(
                client.getId(),
                client.getName(),
                client.getCpf(),
                client.getEmail(),
                client.getPhoneNumber()
        );
    }
}
