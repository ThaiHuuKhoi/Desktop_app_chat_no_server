package com.datn.chatp2p.client.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Requests the user's consent to allow the UDP port range used by ICE.
 */
public final class WindowsIceFirewallPermission {

    private static final Logger LOG = Logger.getLogger(WindowsIceFirewallPermission.class.getName());
    private static final String RULE_NAME = "Chat P2P ICE UDP";
    private static final String PORT_RANGE = "10000-11000";

    private WindowsIceFirewallPermission() {
    }

    public static void requestIfNeeded() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }

        try {
            if (ruleExists()) {
                LOG.info("ICE firewall rule already exists");
                return;
            }
            if (!showConsentDialog()) {
                LOG.info("User declined the ICE firewall permission request");
                return;
            }
            if (!installRuleElevated()) {
                LOG.warning("User did not grant permission for the ICE firewall rule");
            }
        } catch (IOException error) {
            LOG.log(Level.WARNING, "Could not request ICE firewall permission", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Could not request ICE firewall permission", error);
        }
    }

    private static boolean showConsentDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cho phep ket noi P2P");
        alert.setHeaderText("Can cho phep ket noi UDP cho ICE");
        alert.setContentText(
                "Ung dung can mo cong UDP " + PORT_RANGE
                        + " de hai may co the ket noi truc tiep. "
                        + "Windows se hien hop thoai quyen quan tri sau khi ban dong y.");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static boolean ruleExists() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "(Get-NetFirewallRule -DisplayName '" + RULE_NAME
                        + "' -ErrorAction SilentlyContinue) -ne $null")
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return exitCode == 0 && output.trim().equalsIgnoreCase("True");
    }

    private static boolean installRuleElevated() throws IOException, InterruptedException {
        String command = "New-NetFirewallRule -DisplayName '" + RULE_NAME
                + "' -Direction Inbound -Protocol UDP -LocalPort " + PORT_RANGE
                + " -Action Allow -Profile Domain,Private,Public -ErrorAction Stop";
        String encodedCommand = Base64.getEncoder().encodeToString(
                command.getBytes(StandardCharsets.UTF_16LE));
        String launcher = "$p=Start-Process powershell.exe -Verb RunAs -Wait -PassThru "
                + "-ArgumentList '-NoProfile','-NonInteractive','-EncodedCommand','"
                + encodedCommand + "'; exit $p.ExitCode";

        Process process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", launcher)
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            LOG.warning(() -> "ICE firewall rule was not installed, exitCode=" + exitCode);
            return false;
        }
        LOG.info("ICE firewall rule installed for UDP " + PORT_RANGE);
        return true;
    }
}
