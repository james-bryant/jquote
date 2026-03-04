package net.uberfoo.jquote.jquote;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import net.uberfoo.jquote.jquote.quote.QuoteErrorEvent;
import net.uberfoo.jquote.jquote.quote.QuoteService;
import net.uberfoo.jquote.jquote.quote.QuoteUpdateEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Component
public class HelloController {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final QuoteService quoteService;

    @FXML
    private TextField symbolInput;
    @FXML
    private Label statusLabel;
    @FXML
    private Label quoteLabel;

    public HelloController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @FXML
    public void initialize() {
        statusLabel.setText("Enter a symbol and press Enter.");
    }

    @FXML
    protected void onSymbolEntered() {
        String symbol = normalizeSymbol(symbolInput.getText());
        if (symbol.isEmpty()) {
            statusLabel.setText("Symbol is required.");
            quoteLabel.setText("");
            return;
        }
        symbolInput.setText(symbol);
        statusLabel.setText("Fetching quote for " + symbol + "...");
        quoteService.requestQuote(symbol);
    }

    @EventListener
    public void onQuoteUpdate(QuoteUpdateEvent event) {
        Platform.runLater(() -> {
            quoteLabel.setText(event.snapshot().format());
            statusLabel.setText("Updated " + LocalTime.now().format(TIME_FORMAT));
        });
    }

    @EventListener
    public void onQuoteError(QuoteErrorEvent event) {
        Platform.runLater(() -> {
            statusLabel.setText(event.message());
            quoteLabel.setText("");
        });
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase();
    }
}
