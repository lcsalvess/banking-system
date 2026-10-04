package com.lucas.bankingsystem.entity;

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

    public void update(String name, String email, String phoneNumber) {
        if (name != null) {this.name = name;}

        if (email != null) {this.email = email;}

        if (phoneNumber != null) {this.phoneNumber = phoneNumber;}
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCpf() {
        return cpf;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Address getAddress() {
        return address;
    }
}
