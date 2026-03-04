package net.uberfoo.jquote.jquote;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.uberfoo.jquote.jquote.schwab.AuthBrowserLauncher;
import net.uberfoo.jquote.jquote.ui.UiFocusService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

public class HelloApplication extends Application {
    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        context = new SpringApplicationBuilder(JQuoteBoot.class)
                .headless(false)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        fxmlLoader.setControllerFactory(context::getBean);
        Scene scene = new Scene(fxmlLoader.load(), 420, 240);
        stage.setTitle("JQuote");
        stage.setScene(scene);
        stage.show();
        registerStage(stage);
        scheduleAuthBrowserLaunch();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }

    private void scheduleAuthBrowserLaunch() {
        AuthBrowserLauncher launcher = context.getBean(AuthBrowserLauncher.class);
        PauseTransition delay = new PauseTransition(Duration.millis(250));
        delay.setOnFinished(event -> launcher.launchIfNeeded());
        delay.play();
    }

    private void registerStage(Stage stage) {
        UiFocusService focusService = context.getBean(UiFocusService.class);
        focusService.setStage(stage);
    }
}
