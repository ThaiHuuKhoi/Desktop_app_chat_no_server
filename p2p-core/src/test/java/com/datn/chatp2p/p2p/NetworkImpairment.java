package com.datn.chatp2p.p2p;

import java.util.Random;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Gia lap mang xau cho test: MAT goi, NHAN DOI goi, DAO thu tu (tre ngau nhien).
 * Dung truc tiep cho "duong truyen" kieu bat ky ({@link #deliver}) hoac boc 1
 * {@code DataChannel} qua {@link UnreliableDataChannel}.
 *
 * <p>Seed co dinh: quyet dinh mat/nhan doi lap lai duoc giua cac lan chay (thu tu giao goi
 * cuoi cung van phu thuoc lich thread nen co the khac chut it).
 */
public final class NetworkImpairment {

    private final ScheduledExecutorService scheduler;
    private final Random random;
    private volatile double dropRate;
    private final double duplicateRate;
    private final long maxDelayMillis;
    private volatile boolean enabled = true;
    private volatile Predicate<Object> forceDrop = message -> false;

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong duplicated = new AtomicLong();

    /**
     * @param dropRate       xac suat mat moi goi (0..1).
     * @param duplicateRate  xac suat goi bi giao 2 lan.
     * @param maxDelayMillis tre ngau nhien 0..max cho moi ban giao; lon hon 0 thi goi co the toi sai thu tu.
     */
    public NetworkImpairment(ScheduledExecutorService scheduler, long seed,
                             double dropRate, double duplicateRate, long maxDelayMillis) {
        this.scheduler = scheduler;
        this.random = new Random(seed);
        this.dropRate = dropRate;
        this.duplicateRate = duplicateRate;
        this.maxDelayMillis = maxDelayMillis;
    }

    /** Mang hoan hao (van giao bat dong bo qua scheduler, giong mang that). */
    public static NetworkImpairment perfect(ScheduledExecutorService scheduler) {
        return new NetworkImpairment(scheduler, 0, 0, 0, 0);
    }

    /** {@code false} = tam tat moi suy giam (giao ngay, khong mat) - vd trong luc handshake. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Ep MAT moi goi thoa dieu kien (vd "ACK verified dau tien") - dung kich ban cu the. */
    public void setForceDrop(Predicate<Object> forceDrop) {
        this.forceDrop = forceDrop;
    }

    /** Cat hoan toan duong truyen (mat 100%). */
    public void blackHole() {
        this.dropRate = 1.0;
    }

    public <T> void deliver(T message, Consumer<T> receiver) {
        sent.incrementAndGet();
        if (forceDrop.test(message)) {
            dropped.incrementAndGet();
            return;
        }
        if (!enabled) {
            schedule(message, receiver, 0);
            return;
        }
        boolean duplicate;
        long delay1;
        long delay2;
        synchronized (random) {
            if (random.nextDouble() < dropRate) {
                dropped.incrementAndGet();
                return;
            }
            duplicate = random.nextDouble() < duplicateRate;
            delay1 = maxDelayMillis > 0 ? (long) (random.nextDouble() * maxDelayMillis) : 0;
            delay2 = maxDelayMillis > 0 ? (long) (random.nextDouble() * maxDelayMillis) : 0;
        }
        schedule(message, receiver, delay1);
        if (duplicate) {
            duplicated.incrementAndGet();
            schedule(message, receiver, delay2);
        }
    }

    private <T> void schedule(T message, Consumer<T> receiver, long delayMillis) {
        try {
            scheduler.schedule(() -> {
                try {
                    receiver.accept(message);
                } catch (RuntimeException ignored) {
                    // giong P2pDataChannel: loi o ben nhan khong lam sap "mang"
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            // test da ket thuc
        }
    }

    public long sentCount() {
        return sent.get();
    }

    public long droppedCount() {
        return dropped.get();
    }

    public long duplicatedCount() {
        return duplicated.get();
    }
}
