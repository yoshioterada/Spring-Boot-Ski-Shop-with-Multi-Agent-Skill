package com.example.skishop.sales.repository;

import com.example.skishop.sales.model.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

    Page<ReturnRequest> findByOrderId(UUID orderId, Pageable pageable);

    Page<ReturnRequest> findByCustomerId(UUID customerId, Pageable pageable);
}
