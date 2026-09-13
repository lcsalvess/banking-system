package com.lucas.sistemabancario.entity;

import com.lucas.sistemabancario.dto.request.AddressRequestDTO;
import com.lucas.sistemabancario.entity.enums.State;
import com.lucas.sistemabancario.entity.enums.StreetType;
import jakarta.persistence.*;

@Entity
@Table(name = "address")
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StreetType streetType;
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

    public Address(StreetType streetType, String streetName, String streetNumber, String complement, String neighborhood, String city, State state, String postalCode) {
        this.streetType = streetType;
        this.streetName = streetName;
        this.streetNumber = streetNumber;
        this.complement = complement;
        this.neighborhood = neighborhood;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
    }

    public void update(AddressRequestDTO dto) {
        this.streetType = dto.streetType();
        this.streetName = dto.streetName();
        this.streetNumber = dto.streetNumber();
        this.complement = dto.complement();
        this.neighborhood = dto.neighborhood();
        this.city = dto.city();
        this.state = dto.state();
        this.postalCode = dto.postalCode();
    }

    public Long getId() {
        return id;
    }

    public StreetType getStreetType() {
        return streetType;
    }

    public void setStreetType(StreetType streetType) {
        this.streetType = streetType;
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