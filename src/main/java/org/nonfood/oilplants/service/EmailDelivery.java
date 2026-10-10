package org.nonfood.oilplants.service;

public interface EmailDelivery {
    void sendCode(String email,String purpose,String code) throws Exception;
}
