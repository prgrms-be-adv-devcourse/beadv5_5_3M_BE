package com.example.ticketservice.application.service;

import com.example.ticketservice.application.dto.request.RefundCookieRequest;
import com.example.ticketservice.application.port.out.UserPort;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * HTTP 쿠키 차감 후 DB 롤백 시 쿠키를 보상 환불하는 TransactionSynchronization 등록 유틸리티.
 * SelfPaymentService, QueuePurchaseProcessor에서 공통으로 사용.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CookieCompensationHelper {

    public static void registerRollbackRefund(UserPort userPort, Long ticketId, int cookie, UUID userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        userPort.refundCookie(new RefundCookieRequest(ticketId, cookie, userId));
                        log.warn("DB 롤백 쿠키 보상 완료 - ticketId={}, userId={}", ticketId, userId);
                    } catch (Exception e) {
                        log.error("DB 롤백 쿠키 보상 실패 - ticketId={}, userId={}, cookie={}, 수동 처리 필요",
                                ticketId, userId, cookie, e);
                    }
                }
            }
        });
    }
}
