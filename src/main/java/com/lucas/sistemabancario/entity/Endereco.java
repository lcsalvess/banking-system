package com.lucas.sistemabancario.entity;

import com.lucas.sistemabancario.dto.request.EnderecoRequestDTO;
import com.lucas.sistemabancario.entity.enums.Estado;
import com.lucas.sistemabancario.entity.enums.TipoLogradouro;
import jakarta.persistence.*;

@Entity
public class Endereco {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoLogradouro tipoLogradouro;
    @Column(nullable = false, length = 150)
    private String logradouro;
    @Column(nullable = false, length = 10)
    private String numero;
    @Column(length = 100)
    private String complemento;
    @Column(nullable = false, length = 100)
    private String bairro;
    @Column(nullable = false, length = 100)
    private String cidade;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Estado estado;
    @Column(nullable = false, length = 8)
    private String cep;

    public Endereco() {
    }

    public Endereco(TipoLogradouro tipoLogradouro, String logradouro, String numero, String complemento, String bairro, String cidade, Estado estado, String cep) {
        this.tipoLogradouro = tipoLogradouro;
        this.logradouro = logradouro;
        this.numero = numero;
        this.complemento = complemento;
        this.bairro = bairro;
        this.cidade = cidade;
        this.estado = estado;
        this.cep = cep;
    }

    public void atualizarInformacoes(EnderecoRequestDTO dto) {
        this.tipoLogradouro = dto.getTipoLogradouro();
        this.logradouro = dto.getLogradouro();
        this.numero = dto.getNumero();
        this.complemento = dto.getComplemento();
        this.bairro = dto.getBairro();
        this.cidade = dto.getCidade();
        this.estado = dto.getEstado();
        this.cep = dto.getCep();
    }

    public Long getId() {
        return id;
    }

    public TipoLogradouro getTipoLogradouro() {
        return tipoLogradouro;
    }

    public void setTipoLogradouro(TipoLogradouro tipoLogradouro) {
        this.tipoLogradouro = tipoLogradouro;
    }

    public String getLogradouro() {
        return logradouro;
    }

    public void setLogradouro(String logradouro) {
        this.logradouro = logradouro;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getComplemento() {
        return complemento;
    }

    public void setComplemento(String complemento) {
        this.complemento = complemento;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        this.cep = cep;
    }
}