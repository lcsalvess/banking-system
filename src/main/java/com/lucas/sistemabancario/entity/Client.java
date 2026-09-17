package com.lucas.sistemabancario.entity;

import com.lucas.sistemabancario.dto.request.ClientRequestDTO;
import com.lucas.sistemabancario.dto.request.ClientUpdateRequestDTO;
import jakarta.persistence.*;

@Entity
@Table(name = "client",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_client_cpf", columnNames = "cpf"),
                @UniqueConstraint(name = "uk_client_address", columnNames = "address_id")
        })
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 125)
    private String name;
    @Column(nullable = false, length = 11)
    private String cpf;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false, length = 11)
    private String phoneNumber;
    @OneToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "address_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_client_address"))
    private Address address;

    public Client() {
    }

    public Client(String name, String cpf, String email, String phoneNumber, Address address) {
        this.name = name;
        this.cpf = cpf;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.address = address;
    }

    public Client(ClientRequestDTO dto) {
        this.name = dto.name();
        this.cpf = dto.cpf();
        this.email = dto.email();
        this.phoneNumber = dto.phoneNumber();

        this.address = new Address(
                dto.address().streetType(),
                dto.address().streetName(),
                dto.address().streetNumber(),
                dto.address().complement(),
                dto.address().neighborhood(),
                dto.address().city(),
                dto.address().state(),
                dto.address().postalCode()
        );
    }

    public void update(ClientUpdateRequestDTO dto) {
        if (dto.name() != null) {this.name = dto.name();}
        if (dto.email() != null) {this.email = dto.email();}
        if (dto.phoneNumber() != null) {this.phoneNumber = dto.phoneNumber();}
        if (dto.address() != null) {this.address.update(dto.address());}
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }
}
