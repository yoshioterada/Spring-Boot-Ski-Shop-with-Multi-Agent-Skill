package com.example.skishop.usermanagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    private String language;
    private String currency;

    @Column(columnDefinition = "text")
    private String notificationPreferences;

    @Column(columnDefinition = "text")
    private String displayPreferences;
}
