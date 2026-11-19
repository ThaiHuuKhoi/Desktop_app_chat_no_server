package com.datn.chatp2p.p2p.protocol;

import java.util.BitSet;

/**
 * Cua so truot chong phat lai theo SO THU TU goi (cung y tuong DTLS - RFC 6347 muc 4.1.2.6, IPsec
 * anti-replay): nho so lon nhat da nhan va 1 bitmap {@value #SIZE} so ngay duoi no.
 *
 * <ul>
 *   <li>So moi lon hon so lon nhat: nhan, truot cua so len.</li>
 *   <li>So nam trong cua so: nhan neu chua tung thay (UDP co the dao thu tu).</li>
 *   <li>So da thay, hoac CU HON ca cua so: tu choi. Khac voi cach cu (nho van tay 256 goi gan
 *       nhat), goi cu bao lau cung bi chan.</li>
 * </ul>
 *
 * <p>Chi {@link #markSeen} SAU khi goi da giai ma + xac thuc thanh cong - neu khong, ke tan cong
 * gui goi rac mang so thu tu lon co the "day" cua so di va lam goi that bi tu choi.
 */
final class ReplayWindow {

    static final int SIZE = 1024;

    private long highest = -1;
    /** bit i = da thay so {@code highest - i}. */
    private final BitSet seen = new BitSet(SIZE);

    synchronized boolean isFresh(long sequence) {
        if (sequence < 0) {
            return false;
        }
        if (sequence > highest) {
            return true;
        }
        long offset = highest - sequence;
        return offset < SIZE && !seen.get((int) offset);
    }

    synchronized void markSeen(long sequence) {
        if (sequence > highest) {
            long shift = highest < 0 ? SIZE : sequence - highest;
            if (shift >= SIZE) {
                seen.clear();
            } else {
                // Dich bitmap len "shift" vi tri: so cu ra xa hon so lon nhat moi.
                for (int i = SIZE - 1; i >= 0; i--) {
                    int from = i - (int) shift;
                    seen.set(i, from >= 0 && seen.get(from));
                }
            }
            highest = sequence;
            seen.set(0);
        } else {
            long offset = highest - sequence;
            if (offset < SIZE) {
                seen.set((int) offset);
            }
        }
    }
}
