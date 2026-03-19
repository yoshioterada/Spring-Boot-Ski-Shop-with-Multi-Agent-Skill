package com.example.skishop.coupon.repository;

import com.example.skishop.coupon.model.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    Page<Campaign> findByActiveTrue(Pageable pageable);

    List<Campaign> findByActiveTrue();
}
