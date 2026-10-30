package com.datn.chatp2p.common.protocol;

/**
 * Public identity key and its signature, sent only after the encrypted
 * ECDH channel has been established.
 */
public record IdentityPayload(String publicKey, String signature) {
}
