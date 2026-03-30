package com.example.settlementservice.infrastructure.batch;

record PayoutResult(Long settlementId, boolean success) {}