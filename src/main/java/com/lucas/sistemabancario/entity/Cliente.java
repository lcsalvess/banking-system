package com.lucas.sistemabancario.entity;

import com.lucas.sistemabancario.dto.request.ClienteRequestDTO;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 125)
    private String nome;
    @Column(unique = true, nullable = false, length = 11)
    private String cpf;
    @Column(nullable = false, length = 150)
    private String email;
    @Column(nullable = false, length = 11)
    private String telefone;
    @ManyToOne(cascade = CascadeType.PERSIST, optional = false)
    @JoinColumn(name = "endereco_id", nullable = false)
    private Endereco endereco;

    public Cliente () {}

    public Cliente(String nome, String cpf, String email, String telefone, Endereco endereco) {
        this.nome = nome;
        this.cpf = cpf;
        this.email = email;
        this.telefone = telefone;
        this.endereco = endereco;
    }

    public Cliente(ClienteRequestDTO dto) {
        this.nome = dto.getNome();
        this.cpf = dto.getCpf();
        this.email = dto.getEmail();
        this.telefone = dto.getTelefone();

        this.endereco = new Endereco(
                dto.getEndereco().getTipoLogradouro(),
                dto.getEndereco().getLogradouro(),
                dto.getEndereco().getNumero(),
                dto.getEndereco().getComplemento(),
                dto.getEndereco().getBairro(),
                dto.getEndereco().getCidade(),
                dto.getEndereco().getEstado(),
                dto.getEndereco().getCep()
        );
    }

    public void atualizarInformacoes(ClienteRequestDTO dto) {
        this.nome = dto.getNome();
        this.cpf = dto.getCpf();
        this.email = dto.getEmail();
        this.telefone = dto.getTelefone();
        this.endereco.atualizarInformacoes(dto.getEndereco());
    }

    public Long getId() {
        return id;
    }

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

    public Endereco getEndereco() {
        return endereco;
    }

    public void setEndereco(Endereco endereco) {
        this.endereco = endereco;
    }
}
