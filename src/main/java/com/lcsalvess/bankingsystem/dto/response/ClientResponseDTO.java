package com.lcsalvess.bankingsystem.dto.response;

import com.lcsalvess.bankingsystem.entity.Client;
import com.lcsalvess.bankingsystem.util.CpfMasker;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClientResponseDTO(
        @Schema(
                description = "Unique identifier of the client.",
                example = "1"
        )
        Long id,

        @Schema(
                description = "Full name of the client.",
                example = "Pietro Kauê"
        )
        String name,

        @Schema(
                description = "Client's masked CPF.",
                example = "***.***.***-51"
        )
        String cpf,

        @Schema(
                description = "Client's email address.",
                example = "pietro.kaue@example.com"
        )
        String email,

        @Schema(
                description = "Client's phone number.",
                example = "11987654321"
        )
        String phoneNumber,

        @Schema(
                description = "Client's address information."
        )
        AddressResponseDTO address
) {
    public static ClientResponseDTO fromEntity(Client client) {
        return new ClientResponseDTO(
                client.getId(),
                client.getName(),
                CpfMasker.mask(client.getCpf()),
                client.getEmail(),
                client.getPhoneNumber(),
                AddressResponseDTO.fromEntity(client.getAddress())
        );
    }
}
