package com.lcsalvess.bankingsystem.dto.response;

import com.lcsalvess.bankingsystem.entity.Client;

public record ClientResponseDTO(
        Long id,
        String name,
        String cpf,
        String email,
        String phoneNumber,
        AddressResponseDTO address
) {
    public static ClientResponseDTO fromEntity(Client client) {
        return new ClientResponseDTO(
                client.getId(),
                client.getName(),
                client.getCpf(),
                client.getEmail(),
                client.getPhoneNumber(),
                AddressResponseDTO.fromEntity(client.getAddress())
        );
    }
}
