package com.afrochow.outbox.enums;

public enum OutboxStatus {
    PENDING,     // waiting to be dispatched
    PROCESSING,  // claimed by the poller; guards against concurrent double-dispatch
    PROCESSED,   // successfully dispatched
    FAILED       // exhausted retries — needs manual inspection
}
