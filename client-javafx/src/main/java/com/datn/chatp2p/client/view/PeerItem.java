package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.SessionPeer;
import com.datn.chatp2p.common.model.PeerVerificationState;

/** 1 dong trong danh sach nguoi trong phong: peer + trang thai xac thuc do nguoi dung quyet dinh. */
final class PeerItem {

    final SessionPeer peer;
    PeerVerificationState verificationState = PeerVerificationState.UNVERIFIED;

    PeerItem(SessionPeer peer) {
        this.peer = peer;
    }

    boolean isVerified() {
        return verificationState == PeerVerificationState.VERIFIED;
    }
}
