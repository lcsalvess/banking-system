package com.lucas.sistemabancario.dto.response;

import com.lucas.sistemabancario.entity.Client;

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
