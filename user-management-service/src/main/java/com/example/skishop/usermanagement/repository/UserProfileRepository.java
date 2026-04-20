package com.example.skishop.usermanagement.repository;

import com.example.skishop.usermanagement.model.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<UserProfile> findByStatus(UserProfile.UserStatus status, Pageable pageable);

    /**
     * email / firstName / lastName / phoneNumber を部分一致 (大文字小文字無視) で検索する。
     * keyword が空文字や null の場合は呼び出し側で findAll(pageable) を使うこと。
     */
    @Query("SELECT u FROM UserProfile u WHERE " +
            "LOWER(u.email)        LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.firstName)    LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.lastName)     LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(COALESCE(u.phoneNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<UserProfile> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // ============================================================
    // Analytics aggregation queries
    // ============================================================

    /** 期間内に作成されたユーザー数を日別集計 [date, count]。 */
    @Query(value = """
            SELECT CAST(u.created_at AS date) AS d, COUNT(*) AS cnt
            FROM user_profiles u
            WHERE u.created_at >= :since
            GROUP BY CAST(u.created_at AS date)
            ORDER BY d
            """, nativeQuery = true)
    List<Object[]> countDailyRegistrations(@Param("since") Instant since);

    /** ステータス別ユーザー数 [status, count]。 */
    @Query(value = """
            SELECT u.status AS status, COUNT(*) AS cnt
            FROM user_profiles u
            GROUP BY u.status
            """, nativeQuery = true)
    List<Object[]> countByStatus();

    /** 期間内の新規登録ユーザー総数。 */
    @Query(value = """
            SELECT COUNT(*) FROM user_profiles u WHERE u.created_at >= :since
            """, nativeQuery = true)
    long countNewUsersSince(@Param("since") Instant since);
}
