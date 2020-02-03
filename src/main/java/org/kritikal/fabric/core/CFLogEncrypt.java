package org.kritikal.fabric.core;

public interface CFLogEncrypt {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
