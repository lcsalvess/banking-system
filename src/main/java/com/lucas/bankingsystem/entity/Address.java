package com.lucas.bankingsystem.entity;

import com.lucas.bankingsystem.entity.enums.State;
import jakarta.persistence.*;

@Entity
@Table(name = "address")
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String streetName;
    @Column(nullable = false, length = 10)
    private String streetNumber;
    @Column(length = 100)
    private String complement;
    @Column(nullable = false, length = 100)
    private String neighborhood;
    @Column(nullable = false, length = 100)
    private String city;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private State state;
    @Column(nullable = false, length = 8)
    private String postalCode;

    public Address() {
    }

    public Address(String streetName, String streetNumber, String complement, String neighborhood, String city, State state, String postalCode) {
        this.streetName = streetName;
        this.streetNumber = streetNumber;
        this.complement = complement;
        this.neighborhood = neighborhood;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
    }

    public void updateFrom(
            String streetName,
            String streetNumber,
            String complement,
            String neighborhood,
            String city,
            State state,
            String postalCode) {
        this.streetName = streetName;
        this.streetNumber = streetNumber;
        this.complement = complement == null || complement.isBlank()
                ? null
                : complement;
        this.neighborhood = neighborhood;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
    }

    public Long getId() {
        return id;
    }

    public String getStreetName() {
        return streetName;
    }

    public String getStreetNumber() {
        return streetNumber;
    }

    public String getComplement() {
        return complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getCity() {
        return city;
    }

    public State getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }
}