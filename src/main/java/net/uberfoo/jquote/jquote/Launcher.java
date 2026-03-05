package net.uberfoo.jquote.jquote;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        configurePrismPipeline();
        Application.launch(HelloApplication.class, args);
    }

    private static void configurePrismPipeline() {
        String prismOrderProperty = System.getProperty("prism.order");
        if (prismOrderProperty != null && !prismOrderProperty.isBlank()) {
            return;
        }

        String prismOrderEnvironment = System.getenv("JQUOTE_PRISM_ORDER");
        if (prismOrderEnvironment != null && !prismOrderEnvironment.isBlank()) {
            System.setProperty("prism.order", prismOrderEnvironment);
        }
    }
}
