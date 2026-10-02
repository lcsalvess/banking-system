package com.lucas.bankingsystem.entity;

import com.lucas.bankingsystem.dto.request.AddressUpdateRequestDTO;
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

    public void update(AddressUpdateRequestDTO dto) {
        this.streetNumber = dto.streetNumber();
        this.complement = dto.complement() == null || dto.complement().isBlank()
                ? null
                : dto.complement();
    }

    public Long getId() {
        return id;
    }

    public String getStreetName() {
        return streetName;
    }

    public void setStreetName(String streetName) {
        this.streetName = streetName;
    }

    public String getStreetNumber() {
        return streetNumber;
    }

    public void setStreetNumber(String streetNumber) {
        this.streetNumber = streetNumber;
    }

    public String getComplement() {
        return complement;
    }

    public void setComplement(String complement) {
        this.complement = complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
}