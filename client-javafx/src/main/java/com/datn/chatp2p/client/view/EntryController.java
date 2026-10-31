package com.datn.chatp2p.client.view;

import javafx.fxml.FXML;
import javafx.stage.Stage;

/** Selects whether the user joins as a guest or with an account. */
public final class EntryController {

    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void onGuest() {
        Navigator.showHome(stage, false);
    }

    @FXML
    private void onLogin() {
        Navigator.showHome(stage, true);
    }
}
