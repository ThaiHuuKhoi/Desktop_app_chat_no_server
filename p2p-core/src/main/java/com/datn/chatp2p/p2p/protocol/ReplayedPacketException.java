package com.datn.chatp2p.p2p.protocol;

/** Goi co so thu tu da nhan roi (phat lai / nhan doi) hoac cu hon cua so chong replay - bo qua. */
public final class ReplayedPacketException extends IllegalStateException {

    public ReplayedPacketException(long sequence) {
        super("Goi so " + sequence + " da nhan hoac qua cu - bo qua (chong replay)");
    }
}
