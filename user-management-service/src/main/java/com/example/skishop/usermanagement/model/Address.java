package com.example.skishop.usermanagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private AddressType addressType;

    private String recipient;
    private String zipCode;
    private String prefecture;
    private String city;
    private String streetAddress;
    private String building;
    private String phoneNumber;

    @Column(nullable = false)
    private boolean isDefault;
}
