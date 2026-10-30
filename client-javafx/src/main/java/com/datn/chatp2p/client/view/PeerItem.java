package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.SessionPeer;
import com.datn.chatp2p.common.model.PeerVerificationState;

/** 1 dong trong danh sach nguoi trong phong: peer + trang thai identity tu dong. */
final class PeerItem {

    final SessionPeer peer;

    PeerItem(SessionPeer peer) {
        this.peer = peer;
    }

    boolean isVerified() {
        return peer.verificationState() == PeerVerificationState.VERIFIED;
    }
}
