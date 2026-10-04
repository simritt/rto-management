package com.rto.service;

/** Implement for a real SMS/e-mail gateway and expose it as a Spring bean to replace the mock. */
public interface NotificationProvider {
    /** Deliver or throw. */
    void send(String channel, String recipient, String subject, String message) throws Exception;
}
