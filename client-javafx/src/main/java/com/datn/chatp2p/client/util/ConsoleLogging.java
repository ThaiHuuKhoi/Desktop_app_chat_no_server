package com.datn.chatp2p.client.util;

import java.time.Instant;
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
        root.setLevel(Level.INFO);

        Logger.getLogger("com.datn").setLevel(debug ? Level.FINE : Level.INFO);
        Logger.getLogger("org.ice4j").setLevel(debug ? Level.INFO : Level.WARNING);
        Logger.getLogger("org.jitsi").setLevel(debug ? Level.INFO : Level.WARNING);
    }

    private static final class LineFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            String name = record.getLoggerName() == null ? "" : record.getLoggerName();
            String shortName = name.substring(name.lastIndexOf('.') + 1);
            StringBuilder line = new StringBuilder()
                    .append(TIME.format(Instant.ofEpochMilli(record.getMillis()))).append(' ')
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
