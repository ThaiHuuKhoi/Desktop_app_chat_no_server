package com.datn.chatp2p.client.util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

/**
 * Am bao tin nhan moi: 2 not ngan (A5 -&gt; E6) tong hop bang {@code javax.sound} - khong can file
 * am thanh hay module javafx-media. Phat tren thread rieng; dang phat thi bo qua lan moi (tranh
 * don dap khi nhieu tin toi cung luc). May khong co thiet bi am thanh thi im lang.
 */
public final class SoundPlayer {

    private static final Logger LOG = Logger.getLogger(SoundPlayer.class.getName());
    private static final float SAMPLE_RATE = 44_100f;
    private static final byte[] INCOMING = synthesize(new double[] {880, 1318.5}, new int[] {70, 110});

    private static final ExecutorService PLAYER = new ThreadPoolExecutor(1, 1, 0L,
            java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.SynchronousQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "sound-player");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    private SoundPlayer() {
    }

    public static void playIncoming() {
        PLAYER.execute(() -> play(INCOMING));
    }

    private static void play(byte[] pcm) {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            LOG.fine(() -> "Khong phat duoc am bao: " + e.getMessage());
        }
    }

    /** PCM 16-bit mono: moi not la song sin co bao hinh (len nhanh, tat dan) de nghe em tai. */
    private static byte[] synthesize(double[] frequencies, int[] durationsMillis) {
        int total = 0;
        for (int duration : durationsMillis) {
            total += (int) (SAMPLE_RATE * duration / 1000);
        }
        byte[] out = new byte[total * 2];
        int offset = 0;
        for (int n = 0; n < frequencies.length; n++) {
            int samples = (int) (SAMPLE_RATE * durationsMillis[n] / 1000);
            for (int i = 0; i < samples; i++) {
                double t = i / SAMPLE_RATE;
                double attack = Math.min(1.0, i / (SAMPLE_RATE * 0.005));
                double decay = Math.exp(-4.0 * i / samples);
                double value = Math.sin(2 * Math.PI * frequencies[n] * t) * attack * decay * 0.35;
                short sample = (short) (value * Short.MAX_VALUE);
                out[offset++] = (byte) sample;
                out[offset++] = (byte) (sample >> 8);
            }
        }
        return out;
    }
}
