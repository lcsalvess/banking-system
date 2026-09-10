package com.lucas.sistemabancario.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public class ClienteRequestDTO {
    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 125, message = "O nome deve ter no máximo 125 caracteres.")
    private String nome;
    @NotBlank(message = "O CPF é obrigatório.")
    @Pattern(regexp = "^[0-9]{11}$", message = "O CPF deve conter exatamente 11 números.")
    private String cpf;
    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "O formato do e-mail é inválido.")
    @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres.")
    private String email;
    @NotBlank(message = "O telefone é obrigatório.")
    @Pattern(regexp = "^[0-9]{10,11}$", message = "O telefone deve conter de 10 a 11 números, incluindo o DDD.")
    private String telefone;
    @NotNull(message = "Os dados de endereço são obrigatórios.")
    @Valid
    private EnderecoRequestDTO endereco;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public EnderecoRequestDTO getEndereco() {
        return endereco;
    }

    public void setEndereco(EnderecoRequestDTO endereco) {
        this.endereco = endereco;
    }
}
