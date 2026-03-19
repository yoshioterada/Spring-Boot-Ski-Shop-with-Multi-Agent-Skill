package com.example.skishop.point.service;

import com.example.skishop.common.event.DomainEvent;
import com.example.skishop.common.event.EventPublisher;
import com.example.skishop.common.exception.BusinessRuleViolationException;
import com.example.skishop.common.exception.ResourceNotFoundException;
import com.example.skishop.point.dto.*;
import com.example.skishop.point.model.PointTransaction;
import com.example.skishop.point.model.PointTransaction.TransactionType;
import com.example.skishop.point.model.TierDefinition;
import com.example.skishop.point.model.TierDefinition.TierLevel;
import com.example.skishop.point.model.UserTier;
import com.example.skishop.point.repository.PointTransactionRepository;
import com.example.skishop.point.repository.TierDefinitionRepository;
import com.example.skishop.point.repository.UserTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock private UserTierRepository userTierRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private TierDefinitionRepository tierDefinitionRepository;
    @Mock private EventPublisher eventPublisher;

    private PointService pointService;

    private UUID userId;
    private TierDefinition bronzeTier;
    private TierDefinition silverTier;

    @BeforeEach
    void setUp() {
        pointService = new PointService(userTierRepository, pointTransactionRepository,
                tierDefinitionRepository, eventPublisher);
        userId = UUID.randomUUID();
        bronzeTier = new TierDefinition(TierLevel.BRONZE, 0, BigDecimal.ONE, "Entry level");
        silverTier = new TierDefinition(TierLevel.SILVER, 10000, BigDecimal.valueOf(1.25), "Silver tier");
    }

    private void stubTxSave() {
        when(pointTransactionRepository.save(any(PointTransaction.class))).thenAnswer(inv -> {
            PointTransaction tx = inv.getArgument(0);
            try {
                var idField = PointTransaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                if (idField.get(tx) == null) { idField.set(tx, UUID.randomUUID()); }
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            return tx;
        });
    }

    @Nested
    @DisplayName("ポイント付与")
    class AwardPoints {

        @Test
        @DisplayName("既存ユーザーにポイント付与時、ティア倍率が適用される")
        void should_addPointsWithMultiplier_when_existingUser() {
            UserTier userTier = new UserTier(userId, TierLevel.BRONZE);
            var request = new AwardPointsRequest(userId, 100, "Purchase reward", "order-123", 365);

            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.of(userTier));
            when(tierDefinitionRepository.findByLevel(TierLevel.BRONZE)).thenReturn(Optional.of(bronzeTier));
            when(userTierRepository.save(any(UserTier.class))).thenReturn(userTier);
            stubTxSave();
            when(tierDefinitionRepository.findAll()).thenReturn(List.of(bronzeTier, silverTier));

            PointTransactionResponse response = pointService.awardPoints(request);

            assertThat(response.amount()).isEqualTo(100);
            assertThat(response.transactionType()).isEqualTo(TransactionType.EARNED);
            assertThat(response.expiresAt()).isNotNull();
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("新規ユーザーにポイント付与時、UserTierが新規作成される")
        void should_createUserTier_when_newUser() {
            var request = new AwardPointsRequest(userId, 50, "Welcome bonus", null, null);
            UserTier newTier = new UserTier(userId, TierLevel.BRONZE);

            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(userTierRepository.save(any(UserTier.class))).thenReturn(newTier);
            when(tierDefinitionRepository.findByLevel(TierLevel.BRONZE)).thenReturn(Optional.of(bronzeTier));
            stubTxSave();
            when(tierDefinitionRepository.findAll()).thenReturn(List.of(bronzeTier, silverTier));

            PointTransactionResponse response = pointService.awardPoints(request);

            assertThat(response.transactionType()).isEqualTo(TransactionType.EARNED);
            verify(userTierRepository, times(2)).save(any(UserTier.class));
        }
    }

    @Nested
    @DisplayName("ポイント残高照会")
    class GetBalance {

        @Test
        @DisplayName("残高照会が成功する")
        void should_returnBalance_when_userExists() {
            UserTier userTier = new UserTier(userId, TierLevel.BRONZE);
            userTier.addPoints(500);

            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.of(userTier));
            when(tierDefinitionRepository.findByLevel(TierLevel.BRONZE)).thenReturn(Optional.of(bronzeTier));
            when(pointTransactionRepository.sumExpiringPoints(eq(userId), any(Instant.class))).thenReturn(100);

            PointBalanceResponse response = pointService.getBalance(userId);

            assertThat(response.currentBalance()).isEqualTo(500);
            assertThat(response.expiringPoints()).isEqualTo(100);
            assertThat(response.tierLevel()).isEqualTo(TierLevel.BRONZE);
        }

        @Test
        @DisplayName("存在しないユーザーでResourceNotFoundExceptionがスローされる")
        void should_throwNotFound_when_userNotExists() {
            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pointService.getBalance(userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("ポイント取引履歴")
    class GetHistory {

        @Test
        @DisplayName("取引履歴が正しく返される")
        void should_returnHistory() {
            var tx = new PointTransaction(userId, TransactionType.EARNED, 100, 100, "Test", null);
            try {
                var idField = PointTransaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(tx, UUID.randomUUID());
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }

            Pageable pageable = PageRequest.of(0, 20);
            Page<PointTransaction> page = new PageImpl<>(List.of(tx), pageable, 1);
            when(pointTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(page);

            Page<PointTransactionResponse> result = pointService.getHistory(userId, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().getFirst().amount()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("ポイント交換")
    class RedeemPoints {

        @Test
        @DisplayName("十分な残高がある場合、ポイント交換が成功する")
        void should_succeed_when_sufficientBalance() {
            UserTier userTier = new UserTier(userId, TierLevel.BRONZE);
            userTier.addPoints(500);
            var request = new RedeemPointsRequest(userId, 200, "discount", "order-456");

            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.of(userTier));
            when(pointTransactionRepository.save(any(PointTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

            pointService.redeemPoints(request);

            assertThat(userTier.getCurrentBalance()).isEqualTo(300);
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("残高不足の場合、例外がスローされる")
        void should_throwException_when_insufficientBalance() {
            UserTier userTier = new UserTier(userId, TierLevel.BRONZE);
            userTier.addPoints(100);
            var request = new RedeemPointsRequest(userId, 200, "discount", null);

            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.of(userTier));

            assertThatThrownBy(() -> pointService.redeemPoints(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Insufficient points");
        }

        @Test
        @DisplayName("存在しないユーザーで例外がスローされる")
        void should_throwNotFound_when_userNotExists() {
            var request = new RedeemPointsRequest(userId, 100, "discount", null);
            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pointService.redeemPoints(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("ポイント移行")
    class TransferPoints {

        @Test
        @DisplayName("同一ユーザー間の移行で例外がスローされる")
        void should_throwException_when_transferToSameUser() {
            var request = new TransferPointsRequest(userId, userId, 100, "gift");

            assertThatThrownBy(() -> pointService.transferPoints(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("same user");
        }

        @Test
        @DisplayName("有効な移行でポイントが正しく移動する")
        void should_movePointsBetweenUsers_when_validTransfer() {
            UUID toUserId = UUID.randomUUID();
            UserTier fromTier = new UserTier(userId, TierLevel.SILVER);
            fromTier.addPoints(500);
            UserTier toTier = new UserTier(toUserId, TierLevel.BRONZE);
            var request = new TransferPointsRequest(userId, toUserId, 200, "Birthday gift");

            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.of(fromTier));
            when(userTierRepository.findByUserId(toUserId)).thenReturn(Optional.of(toTier));
            when(userTierRepository.save(any(UserTier.class))).thenAnswer(inv -> inv.getArgument(0));
            when(pointTransactionRepository.save(any(PointTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

            pointService.transferPoints(request);

            assertThat(fromTier.getCurrentBalance()).isEqualTo(300);
            assertThat(toTier.getCurrentBalance()).isEqualTo(200);
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("残高不足の移行で例外がスローされる")
        void should_throwException_when_insufficientBalance() {
            UUID toUserId = UUID.randomUUID();
            UserTier fromTier = new UserTier(userId, TierLevel.BRONZE);
            fromTier.addPoints(50);
            var request = new TransferPointsRequest(userId, toUserId, 200, "gift");

            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.of(fromTier));

            assertThatThrownBy(() -> pointService.transferPoints(request))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Insufficient points");
        }
    }

    @Nested
    @DisplayName("ユーザーティア")
    class GetUserTier {

        @Test
        @DisplayName("ユーザーティア取得が成功する")
        void should_returnTier_when_userExists() {
            UserTier userTier = new UserTier(userId, TierLevel.SILVER);
            userTier.addPoints(15000);

            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.of(userTier));
            when(tierDefinitionRepository.findByLevel(TierLevel.SILVER)).thenReturn(Optional.of(silverTier));

            UserTierResponse response = pointService.getUserTier(userId);

            assertThat(response.tierLevel()).isEqualTo(TierLevel.SILVER);
            assertThat(response.totalEarned()).isEqualTo(15000);
        }

        @Test
        @DisplayName("存在しないユーザーで例外がスローされる")
        void should_throwNotFound_when_userNotExists() {
            when(userTierRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pointService.getUserTier(userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("ティア定義")
    class TierDefinitions {

        @Test
        @DisplayName("全ティア定義が返される")
        void should_returnAllTiers() {
            when(tierDefinitionRepository.findAll()).thenReturn(List.of(bronzeTier, silverTier));

            List<TierDefinition> result = pointService.getAllTierDefinitions();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("指定レベルのティア定義が返される")
        void should_returnTierByLevel() {
            when(tierDefinitionRepository.findByLevel(TierLevel.SILVER)).thenReturn(Optional.of(silverTier));

            TierDefinition result = pointService.getTierByLevel(TierLevel.SILVER);

            assertThat(result.getLevel()).isEqualTo(TierLevel.SILVER);
        }

        @Test
        @DisplayName("存在しないティアレベルで例外がスローされる")
        void should_throwNotFound_when_tierNotExists() {
            when(tierDefinitionRepository.findByLevel(TierLevel.PLATINUM)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pointService.getTierByLevel(TierLevel.PLATINUM))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("期限切れポイント")
    class ExpiringPoints {

        @Test
        @DisplayName("期限切れ間近のポイントが返される")
        void should_returnExpiringPoints() {
            var tx = new PointTransaction(userId, TransactionType.EARNED, 100, 100, "Test", null);
            try {
                var idField = PointTransaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(tx, UUID.randomUUID());
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }

            when(pointTransactionRepository.findExpiringPoints(eq(userId), any(Instant.class)))
                    .thenReturn(List.of(tx));

            List<PointTransactionResponse> result = pointService.getExpiringPoints(userId);

            assertThat(result).hasSize(1);
        }
    }
}
