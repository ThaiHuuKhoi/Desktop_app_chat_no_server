package com.datn.chatp2p.client.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.FileHandler;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Log ra terminal de theo doi khi test: moi su kien 1 dong
 * {@code 16:05:12.345 INFO  [P2pChatSession] ...}.
 *
 * <ul>
 *   <li>Mac dinh: INFO tro len cho code cua app ({@code com.datn}), ice4j chi WARNING (rat on).</li>
 *   <li>Chay voi {@code -Dchat.debug=true}: hien ca FINE (gui lai goi, dang go, goi bi bo qua...)
 *       va log chi tiet cua ice4j.</li>
 * </ul>
 * Quy tac: KHONG log noi dung tin nhan, mat khau, token, khoa - chi id, do dai, ten, so luong.
 */
public final class ConsoleLogging {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private ConsoleLogging() {
    }

    public static void install(boolean debug) {
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(Level.ALL);
        console.setFormatter(new LineFormatter());
        root.addHandler(console);
        installFileHandler(root);
        root.setLevel(Level.INFO);

        Logger.getLogger("com.datn").setLevel(debug ? Level.FINE : Level.INFO);
        Logger.getLogger("org.ice4j").setLevel(debug ? Level.INFO : Level.WARNING);
        Logger.getLogger("org.jitsi").setLevel(debug ? Level.INFO : Level.WARNING);
    }

    /** Thu muc log: {@code ~/.chat-p2p-java/logs} (client-0.log moi nhat, toi da 5 file x 5 MB). */
    public static Path logDirectory() {
        return Path.of(System.getProperty("user.home"), ".chat-p2p-java", "logs");
    }

    /**
     * Ghi them ra file - chay app khong co terminal (ban cai dat, bam dup jar) van con log de tim loi.
     * Cung quy tac che bi mat nhu console (SecretLogging ap dung ngay tu cho goi log).
     */
    private static void installFileHandler(Logger root) {
        try {
            Files.createDirectories(logDirectory());
            FileHandler file = new FileHandler(
                    logDirectory().resolve("client-%g.log").toString(), 5 * 1024 * 1024, 5, true);
            file.setEncoding(StandardCharsets.UTF_8.name());
            file.setLevel(Level.ALL);
            file.setFormatter(new LineFormatter(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault())));
            root.addHandler(file);
        } catch (IOException | RuntimeException e) {
            System.err.println("Khong ghi duoc log ra file (" + logDirectory() + "): " + e.getMessage());
        }
    }

    private static final class LineFormatter extends Formatter {
        private final DateTimeFormatter time;

        LineFormatter() {
            this(TIME);
        }

        LineFormatter(DateTimeFormatter time) {
            this.time = time;
        }

        @Override
        public String format(LogRecord record) {
            String name = record.getLoggerName() == null ? "" : record.getLoggerName();
            String shortName = name.substring(name.lastIndexOf('.') + 1);
            StringBuilder line = new StringBuilder()
                    .append(time.format(Instant.ofEpochMilli(record.getMillis()))).append(' ')
                    .append(String.format("%-7s", record.getLevel().getName())).append('[')
                    .append(shortName).append("] ")
                    .append(formatMessage(record)).append(System.lineSeparator());
            if (record.getThrown() != null) {
                line.append("    ").append(record.getThrown()).append(System.lineSeparator());
            }
            return line.toString();
        }
    }
}
