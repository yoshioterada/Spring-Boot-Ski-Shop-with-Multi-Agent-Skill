package com.example.skishop.ai.repository;

import com.example.skishop.ai.model.UserProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserProfileRepository extends MongoRepository<UserProfile, String> {

    List<UserProfile> findByLoyaltyTier(String loyaltyTier);
}
