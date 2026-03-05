package net.uberfoo.jquote.jquote;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        configurePrismPipeline();
        Application.launch(HelloApplication.class, args);
    }

    private static void configurePrismPipeline() {
        String prismOrder = System.getProperty("prism.order");
        if (prismOrder == null || prismOrder.isBlank()) {
            // Use software rendering by default to avoid known D3D Canvas texture failures on some Windows drivers.
            System.setProperty("prism.order", "sw");
        }
    }
}
