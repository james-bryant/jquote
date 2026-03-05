package net.uberfoo.jquote.jquote.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class UiFocusService {
    private final AtomicReference<Stage> stageRef = new AtomicReference<>();

    public void setStage(Stage stage) {
        stageRef.set(stage);
    }

    public void requestFocus() {
        Stage stage = stageRef.get();
        if (stage == null) {
            return;
        }
        Platform.runLater(() -> {
            if (!stage.isShowing()) {
                return;
            }
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
        });
    }
}
