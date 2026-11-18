package com.lcsalvess.bankingsystem.dto.response.client;

import com.lcsalvess.bankingsystem.dto.response.address.AddressResponseDto;
import com.lcsalvess.bankingsystem.entity.client.Client;
import com.lcsalvess.bankingsystem.util.privacy.CpfMasker;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClientResponseDto(
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
    AddressResponseDto address
) {
  public static ClientResponseDto fromEntity(Client client) {
    return new ClientResponseDto(
        client.getId(),
        client.getName(),
        CpfMasker.mask(client.getCpf()),
        client.getEmail(),
        client.getPhoneNumber(),
        AddressResponseDto.fromEntity(client.getAddress())
    );
  }
}
