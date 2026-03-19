package com.example.skishop.mailsend.repository;

import com.example.skishop.mailsend.model.MailLog;
import com.example.skishop.mailsend.model.MailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailLogRepository extends JpaRepository<MailLog, UUID> {

    Optional<MailLog> findByEventId(String eventId);

    Page<MailLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<MailLog> findByStatus(MailStatus status);

    @Query("select count(m) from MailLog m where m.status = :status")
    long countByStatus(@Param("status") MailStatus status);

    @Query("select count(m) from MailLog m where m.status = 'SENT'")
    long countSent();

    @Query("select count(m) from MailLog m where m.status = 'FAILED'")
    long countFailed();

    @Query("select count(m) from MailLog m where m.status in ('PENDING','SENDING')")
    long countPending();

    @Query("select m.templateName, count(m) from MailLog m where m.status = 'SENT' and m.createdAt >= :since group by m.templateName")
    List<Object[]> countSentByTemplateSince(@Param("since") Instant since);
}
