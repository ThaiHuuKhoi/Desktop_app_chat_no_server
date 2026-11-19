package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.FileTransferStatus;
import com.datn.chatp2p.client.session.FileTransferStatus.Direction;

import java.util.Locale;

/** Chuoi hien thi cho the file: dung luong, trang thai, ly do loi (tieng Viet). */
final class FileTransferTexts {

    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    private FileTransferTexts() {
    }

    static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(VI, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(VI, "%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format(VI, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** Dong trang thai duoi ten file. {@code who} = ten nguoi ben kia. */
    static String status(FileTransferStatus s, String who) {
        boolean outgoing = s.direction() == Direction.OUTGOING;
        return switch (s.state()) {
            case WAITING_ANSWER -> "Đang chờ " + who + " chấp nhận…";
            case OFFERED -> who + " muốn gửi tệp này cho bạn";
            case IN_PROGRESS -> (outgoing ? "Đang gửi tới " + who : "Đang nhận")
                    + " · " + Math.round(s.progress() * 100) + "% · "
                    + size(s.bytesTransferred()) + " / " + size(s.fileSize());
            case VERIFYING -> "Đang kiểm tra toàn vẹn (SHA-256)…";
            case COMPLETED -> outgoing
                    ? "Đã gửi tới " + who + " · đã kiểm tra SHA-256"
                    : "Đã nhận · đã kiểm tra SHA-256";
            case REJECTED -> rejected(s, who, outgoing);
            case CANCELLED -> FileTransferStatus.CANCELLED_BY_PEER.equals(s.reason())
                    ? who + " đã huỷ"
                    : "Bạn đã huỷ";
            case FAILED -> "Thất bại: " + failure(s.reason(), who);
        };
    }

    private static String rejected(FileTransferStatus s, String who, boolean outgoing) {
        String reason = s.reason();
        if (outgoing) {
            if ("FILE_TOO_LARGE".equals(reason)) {
                return who + " không nhận: tệp quá lớn";
            }
            if ("INVALID_OFFER".equals(reason)) {
                return who + " không nhận: lời mời không hợp lệ";
            }
            if ("BUSY".equals(reason)) {
                return who + " đang nhận một tệp khác, hãy gửi lại sau";
            }
            return who + " đã từ chối";
        }
        if ("BUSY".equals(reason)) {
            return "Đã tự động từ chối: bạn đang nhận một tệp khác từ " + who;
        }
        if ("FILE_TOO_LARGE".equals(reason)) {
            return "Đã tự động từ chối: tệp vượt giới hạn dung lượng";
        }
        if ("INVALID_OFFER".equals(reason)) {
            return "Đã tự động từ chối: thông tin tệp không hợp lệ";
        }
        return "Bạn đã từ chối";
    }

    private static String failure(String reason, String who) {
        if (reason == null) {
            return "lỗi không xác định";
        }
        return switch (reason) {
            case "TIMEOUT" -> "hết thời gian chờ, kết nối có thể đã bị gián đoạn";
            case "CHECKSUM_MISMATCH" -> "tệp bị hỏng khi truyền (SHA-256 không khớp), đã xoá";
            case "IO_ERROR" -> "lỗi đọc/ghi tệp";
            case "FILE_TOO_LARGE" -> "tệp quá lớn";
            case "INVALID_OFFER" -> "thông tin tệp không hợp lệ";
            case FileTransferStatus.PEER_LEFT -> who + " đã rời phòng";
            case FileTransferStatus.SEND_FAILED -> "không gửi được, mất kết nối";
            default -> reason;
        };
    }
}
