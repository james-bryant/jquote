package net.uberfoo.jquote.jquote;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import net.uberfoo.jquote.jquote.quote.DailyOhlcPoint;
import net.uberfoo.jquote.jquote.quote.HistoricalDailyErrorEvent;
import net.uberfoo.jquote.jquote.quote.HistoricalDailyService;
import net.uberfoo.jquote.jquote.quote.HistoricalDailyUpdateEvent;
import net.uberfoo.jquote.jquote.quote.HistoricalRangePreset;
import net.uberfoo.jquote.jquote.quote.IntradayErrorEvent;
import net.uberfoo.jquote.jquote.quote.IntradayPoint;
import net.uberfoo.jquote.jquote.quote.IntradayService;
import net.uberfoo.jquote.jquote.quote.IntradaySession;
import net.uberfoo.jquote.jquote.quote.IntradayUpdateEvent;
import net.uberfoo.jquote.jquote.quote.QuoteErrorEvent;
import net.uberfoo.jquote.jquote.quote.QuoteService;
import net.uberfoo.jquote.jquote.quote.QuoteSnapshot;
import net.uberfoo.jquote.jquote.quote.QuoteUpdateEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

@Component
public class HelloController {
    private static final DateTimeFormatter OVERVIEW_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_AXIS_FORMAT = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter INTRADAY_AXIS_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Color CHART_BACKGROUND_COLOR = Color.web("#000000");
    private static final Color CHART_POSITIVE_COLOR = Color.web("#39a04a");
    private static final Color CHART_NEGATIVE_COLOR = Color.web("#d85d5d");
    private static final Color CHART_AXIS_COLOR = Color.web("#435566");
    private static final Color CHART_LABEL_COLOR = Color.web("#b7c5d4");
    private static final Color BOLLINGER_COLOR = Color.web("#6aa6e8");
    private static final Color BOLLINGER_MID_COLOR = Color.web("#f2cb5a");
    private static final Color RSI_COLOR = Color.web("#7ed957");
    private static final Color RSI_GUIDE_COLOR = Color.web("#4c5967");
    private static final Color MACD_COLOR = Color.web("#6aa6e8");
    private static final Color MACD_SIGNAL_COLOR = Color.web("#f2cb5a");
    private static final double CHART_LEFT_PAD = 56;
    private static final double CHART_RIGHT_PAD = 16;
    private static final int INTRADAY_TARGET_SLOT_WIDTH_PX = 8;
    private static final int INTRADAY_MIN_VISIBLE_POINTS = 24;
    private static final int CHART_MIN_ZOOM_VISIBLE_POINTS = 12;

    private final QuoteService quoteService;
    private final IntradayService intradayService;
    private final HistoricalDailyService historicalDailyService;
    private String activeSymbol = "";
    private IntradayViewState intradayViewState = IntradayViewState.IDLE;
    private HistoricalRangePreset historicalPreset = HistoricalRangePreset.DAYS_30;
    private List<DailyOhlcPoint> historicalPoints = List.of();
    private List<IntradayPoint> intradayPoints = List.of();
    private QuoteSnapshot latestQuoteSnapshot;
    private boolean extendedHoursSupported = true;
    private boolean suppressSessionChangeHandler = false;
    private boolean rsiEnabled = false;
    private boolean macdEnabled = false;
    private boolean bollingerEnabled = false;
    private boolean suppressIndicatorHandler = false;
    private int intradayViewportStart = 0;
    private int intradayViewportSize = 0;
    private boolean intradayPanActive = false;
    private double intradayPanAnchorX = 0;
    private int intradayPanAnchorStart = 0;
    private int historicalViewportStart = 0;
    private int historicalViewportSize = 0;
    private boolean historicalPanActive = false;
    private double historicalPanAnchorX = 0;
    private int historicalPanAnchorStart = 0;
    private List<IntradayPoint> intradayVisiblePoints = List.of();
    private List<DailyOhlcPoint> historicalVisiblePoints = List.of();
    private double[] intradayVisibleRsi = new double[0];
    private double[] intradayVisibleMacd = new double[0];
    private double[] intradayVisibleMacdSignal = new double[0];
    private double[] intradayVisibleMacdHistogram = new double[0];
    private double[] intradayVisibleBollingerUpper = new double[0];
    private double[] intradayVisibleBollingerMiddle = new double[0];
    private double[] intradayVisibleBollingerLower = new double[0];
    private double[] historicalVisibleRsi = new double[0];
    private double[] historicalVisibleMacd = new double[0];
    private double[] historicalVisibleMacdSignal = new double[0];
    private double[] historicalVisibleMacdHistogram = new double[0];
    private double[] historicalVisibleBollingerUpper = new double[0];
    private double[] historicalVisibleBollingerMiddle = new double[0];
    private double[] historicalVisibleBollingerLower = new double[0];
    private final Tooltip intradayTooltip = createChartTooltip();
    private final Tooltip historicalTooltip = createChartTooltip();
    private final PauseTransition intradayTooltipDelay = createTooltipDelay();
    private final PauseTransition historicalTooltipDelay = createTooltipDelay();
    private double intradayHoverCanvasX = -1;
    private double intradayHoverScreenX = -1;
    private double intradayHoverScreenY = -1;
    private double historicalHoverCanvasX = -1;
    private double historicalHoverScreenX = -1;
    private double historicalHoverScreenY = -1;

    @FXML private TextField symbolInput;
    @FXML private ComboBox<IntradaySession> sessionChoice;
    @FXML private CheckBox historicalRsiToggle;
    @FXML private CheckBox historicalMacdToggle;
    @FXML private CheckBox historicalBollingerToggle;
    @FXML private CheckBox intradayRsiToggle;
    @FXML private CheckBox intradayMacdToggle;
    @FXML private CheckBox intradayBollingerToggle;
    @FXML private TabPane chartTabPane;
    @FXML private Label statusLabel;
    @FXML private Label historicalStatusLabel;
    @FXML private Label intradayTimestampLabel;
    @FXML private Label historicalTimestampLabel;
    @FXML private Label overviewHeadlineLabel;
    @FXML private Label overviewSignalLabel;
    @FXML private Label overviewQuoteStatsLabel;
    @FXML private Label overviewIntradayStatsLabel;
    @FXML private Label overviewHistoricalStatsLabel;
    @FXML private Label overviewIndicatorStatsLabel;
    @FXML private Label overviewComputedStatsLabel;
    @FXML private Label historicalValidationLabel;
    @FXML private Label historicalErrorBannerLabel;
    @FXML private Label historicalEmptyLabel;
    @FXML private Label intradayEmptyLabel;
    @FXML private Button retryIntradayButton;
    @FXML private Button historicalApplyRangeButton;
    @FXML private Button historicalRetryButton;
    @FXML private Button historicalPreset30Button;
    @FXML private Button historicalPreset90Button;
    @FXML private Button historicalPreset120Button;
    @FXML private DatePicker historicalStartDatePicker;
    @FXML private DatePicker historicalEndDatePicker;
    @FXML private Canvas historicalChartCanvas;
    @FXML private Canvas intradayChartCanvas;

    public HelloController(QuoteService quoteService,
                           IntradayService intradayService,
                           HistoricalDailyService historicalDailyService) {
        this.quoteService = quoteService;
        this.intradayService = intradayService;
        this.historicalDailyService = historicalDailyService;
    }

    @FXML
    public void initialize() {
        chartTabPane.getSelectionModel().select(0);
        statusLabel.setText("Enter a symbol and press Enter.");
        configureSessionChoice();
        configureIndicatorControls();
        configureHistoricalControls();
        configureChartTooltipDelays();
        configureIntradayCanvas();
        configureHistoricalCanvas();
        clearQuoteState();
        clearTabTimestamps();
        hideRetry();
        setIntradayChartVisible(true);
        updateOverviewSummary();
    }

    @FXML
    protected void onSymbolEntered() {
        String symbol = normalizeSymbol(symbolInput.getText());
        if (symbol.isEmpty()) {
            statusLabel.setText("Symbol is required.");
            clearQuoteState();
            clearIntradayChart();
            showIntradayEmptyMessage("Enter a symbol to load intraday candles.");
            setIntradayChartVisible(false);
            hideRetry();
            clearHistoricalChart();
            historicalStatusLabel.setText("Symbol is required.");
            showHistoricalEmptyMessage("Enter a symbol to load historical daily candles.");
            clearTabTimestamps();
            updateOverviewSummary();
            return;
        }
        requestAllContextsForSymbol(symbol);
        updateOverviewSummary();
    }

    @FXML
    protected void onHistoricalPreset30() {
        applyHistoricalPreset(HistoricalRangePreset.DAYS_30);
    }

    @FXML
    protected void onHistoricalPreset90() {
        applyHistoricalPreset(HistoricalRangePreset.DAYS_90);
    }

    @FXML
    protected void onHistoricalPreset120() {
        applyHistoricalPreset(HistoricalRangePreset.DAYS_120);
    }

    @FXML
    protected void onHistoricalCustomRangeApplied() {
        HistoricalInputValidation validation = validateHistoricalInputs();
        if (!validation.valid()) {
            historicalStatusLabel.setText("Fix highlighted historical inputs before fetching.");
            showHistoricalValidationMessage(validation.message());
            hideHistoricalErrorBanner();
            updateOverviewSummary();
            return;
        }
        hideHistoricalValidationMessage();

        String symbol = validation.symbol();
        LocalDate startDate = validation.startDate();
        LocalDate endDate = validation.endDate();
        boolean symbolChanged = syncActiveSymbol(symbol);
        if (symbolChanged) {
            refreshOverviewAndIntraday(symbol);
        }
        historicalStatusLabel.setText("Loading custom daily range for " + symbol + "...");
        hideHistoricalEmptyMessage();
        hideHistoricalErrorBanner();
        clearHistoricalChart();
        historicalDailyService.requestDailyHistory(symbol, startDate, endDate);
        updateOverviewSummary();
    }

    @FXML
    protected void onRetryHistorical() {
        HistoricalInputValidation validation = validateHistoricalInputs();
        if (!validation.valid()) {
            historicalStatusLabel.setText("Fix highlighted historical inputs before retrying.");
            showHistoricalValidationMessage(validation.message());
            updateOverviewSummary();
            return;
        }
        hideHistoricalValidationMessage();
        hideHistoricalErrorBanner();
        hideHistoricalRetry();
        historicalStatusLabel.setText("Retrying historical daily candles for " + validation.symbol() + "...");
        hideHistoricalEmptyMessage();
        clearHistoricalChart();
        historicalDailyService.requestDailyHistory(validation.symbol(), validation.startDate(), validation.endDate());
        updateOverviewSummary();
    }

    @FXML
    protected void onIndicatorToggleChanged(ActionEvent event) {
        if (suppressIndicatorHandler || !(event.getSource() instanceof CheckBox source)) {
            return;
        }
        boolean selected = source.isSelected();
        if (source == historicalRsiToggle || source == intradayRsiToggle) {
            rsiEnabled = selected;
        } else if (source == historicalMacdToggle || source == intradayMacdToggle) {
            macdEnabled = selected;
        } else if (source == historicalBollingerToggle || source == intradayBollingerToggle) {
            bollingerEnabled = selected;
        } else {
            return;
        }
        syncIndicatorControls();
        redrawHistoricalChart();
        redrawIntradayChart();
        updateOverviewSummary();
    }

    @FXML
    protected void onSessionChanged() {
        if (suppressSessionChangeHandler) {
            return;
        }
        if (activeSymbol.isBlank()) {
            return;
        }
        clearIntradayChart();
        hideIntradayEmptyMessage();
        setIntradayChartVisible(true);
        hideRetry();
        intradayViewState = IntradayViewState.LOADING;
        statusLabel.setText("Loading " + selectedSession().label() + " for " + activeSymbol + "...");
        intradayService.requestIntraday(activeSymbol, selectedSession());
        updateOverviewSummary();
    }

    @FXML
    protected void onRetryIntraday() {
        if (activeSymbol.isBlank()) {
            return;
        }
        clearIntradayChart();
        hideIntradayEmptyMessage();
        setIntradayChartVisible(true);
        hideRetry();
        intradayViewState = IntradayViewState.LOADING;
        statusLabel.setText("Retrying intraday for " + activeSymbol + "...");
        intradayService.requestIntraday(activeSymbol, selectedSession());
        updateOverviewSummary();
    }

    @EventListener
    public void onQuoteUpdate(QuoteUpdateEvent event) {
        if (!isActiveSymbol(event.snapshot().symbol())) {
            return;
        }
        Platform.runLater(() -> {
            latestQuoteSnapshot = event.snapshot();
            if (intradayViewState != IntradayViewState.MARKET_CLOSED
                    && intradayViewState != IntradayViewState.ERROR
                    && intradayPoints.isEmpty()) {
                statusLabel.setText("Quote updated.");
            }
            updateOverviewSummary();
        });
    }

    @EventListener
    public void onQuoteError(QuoteErrorEvent event) {
        if (!isActiveSymbol(event.symbol())) {
            return;
        }
        Platform.runLater(() -> {
            statusLabel.setText(event.message());
            latestQuoteSnapshot = null;
            clearQuoteState();
            updateOverviewSummary();
        });
    }

    @EventListener
    public void onIntradayUpdate(IntradayUpdateEvent event) {
        if (!isActiveSymbol(event.snapshot().symbol())) {
            return;
        }
        Platform.runLater(() -> {
            updateSessionAvailability(event.snapshot().session(), event.snapshot().extendedHoursSupported());
            hideRetry();
            if (event.snapshot().points().isEmpty()) {
                intradayViewState = IntradayViewState.MARKET_CLOSED;
                clearIntradayChart();
                showIntradayEmptyMessage("No intraday candles available for this session.");
                setIntradayChartVisible(false);
                statusLabel.setText("Market closed. Showing previous close summary only.");
                intradayTimestampLabel.setText(nowTimestamp());
                updateOverviewSummary();
                return;
            }
            intradayViewState = IntradayViewState.HAS_DATA;
            hideIntradayEmptyMessage();
            setIntradayChartVisible(true);
            renderIntradayCandles(event.snapshot().points());
            statusLabel.setText("Intraday updated.");
            intradayTimestampLabel.setText(nowTimestamp());
            updateOverviewSummary();
        });
    }

    @EventListener
    public void onIntradayError(IntradayErrorEvent event) {
        if (!isActiveSymbol(event.symbol())) {
            return;
        }
        Platform.runLater(() -> {
            intradayViewState = IntradayViewState.ERROR;
            clearIntradayChart();
            showIntradayEmptyMessage("Intraday data unavailable. Retry or switch session.");
            setIntradayChartVisible(false);
            statusLabel.setText(event.message());
            showRetry();
            updateOverviewSummary();
        });
    }

    @EventListener
    public void onHistoricalUpdate(HistoricalDailyUpdateEvent event) {
        if (!isActiveSymbol(event.snapshot().symbol())) {
            return;
        }
        Platform.runLater(() -> {
            hideHistoricalValidationMessage();
            hideHistoricalErrorBanner();
            hideHistoricalRetry();
            hideHistoricalEmptyMessage();
            historicalStartDatePicker.setValue(event.snapshot().range().startDate());
            historicalEndDatePicker.setValue(event.snapshot().range().endDate());
            historicalPoints = event.snapshot().points();
            resetHistoricalViewport();
            if (historicalPoints.isEmpty()) {
                clearHistoricalChart();
                showHistoricalEmptyMessage("No historical daily data available for this range.");
                historicalStatusLabel.setText("No daily candles returned for " + event.snapshot().symbol() + ".");
                historicalTimestampLabel.setText(nowTimestamp());
                updateOverviewSummary();
                return;
            }
            renderHistoricalCandles(historicalPoints);
            historicalStatusLabel.setText("Historical updated.");
            historicalTimestampLabel.setText(nowTimestamp());
            updateOverviewSummary();
        });
    }

    @EventListener
    public void onHistoricalError(HistoricalDailyErrorEvent event) {
        if (!isActiveSymbol(event.symbol())) {
            return;
        }
        Platform.runLater(() -> {
            historicalStatusLabel.setText(event.message());
            showHistoricalErrorBanner(event.message());
            showHistoricalRetry();
            clearHistoricalChart();
            showHistoricalEmptyMessage("Historical data unavailable. Adjust the range or try again.");
            updateOverviewSummary();
        });
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase();
    }

    private void renderIntradayCandles(List<IntradayPoint> points) {
        if (points == null || points.isEmpty()) {
            clearIntradayChart();
            return;
        }

        intradayPoints = points;
        double width = intradayChartCanvas.getWidth();
        double height = intradayChartCanvas.getHeight();
        if (!hasRenderableSize(width, height)) {
            return;
        }
        GraphicsContext graphics = intradayChartCanvas.getGraphicsContext2D();
        graphics.setFill(CHART_BACKGROUND_COLOR);
        graphics.fillRect(0, 0, width, height);

        ChartLayout layout = createChartLayout(width, height, rsiEnabled, macdEnabled);
        WindowRange intradayWindow = resolveIntradayWindow(points.size(), layout.pricePanel().width());
        List<IntradayPoint> visiblePoints = points.subList(intradayWindow.startInclusive(), intradayWindow.endExclusive());
        intradayVisiblePoints = List.copyOf(visiblePoints);
        List<Double> closes = visiblePoints.stream().map(point -> point.close().doubleValue()).toList();
        IndicatorSeries bollinger = computeBollingerBands(closes, 20, 2.0);
        intradayVisibleBollingerUpper = bollinger.upper();
        intradayVisibleBollingerMiddle = bollinger.middle();
        intradayVisibleBollingerLower = bollinger.lower();
        intradayVisibleRsi = computeRsi(closes, 14);
        String firstLabel = visiblePoints.getFirst().timestamp().format(INTRADAY_AXIS_FORMAT);
        String lastLabel = visiblePoints.getLast().timestamp().format(INTRADAY_AXIS_FORMAT);
        drawPricePanel(graphics, layout, visiblePoints.size(), firstLabel, lastLabel,
                visiblePoints.stream().mapToDouble(point -> point.open().doubleValue()).toArray(),
                visiblePoints.stream().mapToDouble(point -> point.high().doubleValue()).toArray(),
                visiblePoints.stream().mapToDouble(point -> point.low().doubleValue()).toArray(),
                visiblePoints.stream().mapToDouble(point -> point.close().doubleValue()).toArray(),
                bollingerEnabled ? bollinger : null);

        if (rsiEnabled) {
            drawRsiPanel(graphics, layout, visiblePoints.size(), intradayVisibleRsi, firstLabel, lastLabel);
        }
        if (macdEnabled) {
            MacdSeries macdSeries = computeMacd(closes, 12, 26, 9);
            intradayVisibleMacd = macdSeries.macd();
            intradayVisibleMacdSignal = macdSeries.signal();
            intradayVisibleMacdHistogram = macdSeries.histogram();
            drawMacdPanel(graphics, layout, visiblePoints.size(), macdSeries, firstLabel, lastLabel);
        } else {
            intradayVisibleMacd = new double[0];
            intradayVisibleMacdSignal = new double[0];
            intradayVisibleMacdHistogram = new double[0];
        }
        updateIntradayCursor();
    }

    private void clearQuoteState() {
        latestQuoteSnapshot = null;
    }

    private void clearTabTimestamps() {
        intradayTimestampLabel.setText("");
        historicalTimestampLabel.setText("");
    }

    private String nowTimestamp() {
        return LocalDateTime.now().format(OVERVIEW_TIMESTAMP_FORMAT);
    }

    private Tooltip createChartTooltip() {
        Tooltip tooltip = new Tooltip();
        tooltip.setShowDelay(javafx.util.Duration.ZERO);
        tooltip.setHideDelay(javafx.util.Duration.millis(60));
        tooltip.setShowDuration(javafx.util.Duration.seconds(30));
        return tooltip;
    }

    private PauseTransition createTooltipDelay() {
        return new PauseTransition(javafx.util.Duration.millis(500));
    }

    private String buildIntradayTooltipText(double mouseX) {
        int index = resolveHoveredVisibleIndex(mouseX, intradayVisiblePoints.size(), intradayChartCanvas.getWidth());
        if (index < 0) {
            return null;
        }
        IntradayPoint point = intradayVisiblePoints.get(index);
        return "Time: " + point.timestamp().format(OVERVIEW_TIMESTAMP_FORMAT) + "\n"
                + "Open: " + formatBigDecimal(point.open()) + "\n"
                + "High: " + formatBigDecimal(point.high()) + "\n"
                + "Low: " + formatBigDecimal(point.low()) + "\n"
                + "Close: " + formatBigDecimal(point.close()) + "\n"
                + "Volume: " + formatWhole(point.volume() == null ? 0 : point.volume()) + "\n"
                + "RSI14: " + formatSeriesValue(intradayVisibleRsi, index, 2) + "\n"
                + "MACD: " + formatSeriesValue(intradayVisibleMacd, index, 3) + "\n"
                + "Signal: " + formatSeriesValue(intradayVisibleMacdSignal, index, 3) + "\n"
                + "Hist: " + formatSeriesValue(intradayVisibleMacdHistogram, index, 3) + "\n"
                + "BB Upper: " + formatSeriesValue(intradayVisibleBollingerUpper, index, 2) + "\n"
                + "BB Mid: " + formatSeriesValue(intradayVisibleBollingerMiddle, index, 2) + "\n"
                + "BB Lower: " + formatSeriesValue(intradayVisibleBollingerLower, index, 2);
    }

    private String buildHistoricalTooltipText(double mouseX) {
        int index = resolveHoveredVisibleIndex(mouseX, historicalVisiblePoints.size(), historicalChartCanvas.getWidth());
        if (index < 0) {
            return null;
        }
        DailyOhlcPoint point = historicalVisiblePoints.get(index);
        return "Time: " + point.date().atStartOfDay().format(OVERVIEW_TIMESTAMP_FORMAT) + "\n"
                + "Open: " + formatBigDecimal(point.open()) + "\n"
                + "High: " + formatBigDecimal(point.high()) + "\n"
                + "Low: " + formatBigDecimal(point.low()) + "\n"
                + "Close: " + formatBigDecimal(point.close()) + "\n"
                + "Volume: " + formatWhole(point.volume() == null ? 0 : point.volume()) + "\n"
                + "RSI14: " + formatSeriesValue(historicalVisibleRsi, index, 2) + "\n"
                + "MACD: " + formatSeriesValue(historicalVisibleMacd, index, 3) + "\n"
                + "Signal: " + formatSeriesValue(historicalVisibleMacdSignal, index, 3) + "\n"
                + "Hist: " + formatSeriesValue(historicalVisibleMacdHistogram, index, 3) + "\n"
                + "BB Upper: " + formatSeriesValue(historicalVisibleBollingerUpper, index, 2) + "\n"
                + "BB Mid: " + formatSeriesValue(historicalVisibleBollingerMiddle, index, 2) + "\n"
                + "BB Lower: " + formatSeriesValue(historicalVisibleBollingerLower, index, 2);
    }

    private int resolveHoveredVisibleIndex(double mouseX, int visibleCount, double canvasWidth) {
        if (visibleCount <= 0) {
            return -1;
        }
        double panelWidth = Math.max(1, canvasWidth - CHART_LEFT_PAD - CHART_RIGHT_PAD);
        if (mouseX < CHART_LEFT_PAD || mouseX > CHART_LEFT_PAD + panelWidth) {
            return -1;
        }
        double ratio = (mouseX - CHART_LEFT_PAD) / panelWidth;
        int index = (int) Math.floor(ratio * visibleCount);
        if (index >= visibleCount) {
            index = visibleCount - 1;
        }
        return Math.max(0, index);
    }

    private String formatSeriesValue(double[] series, int index, int scale) {
        if (series == null || index < 0 || index >= series.length) {
            return "n/a";
        }
        return formatDouble(series[index], scale);
    }

    private void updateOverviewSummary() {
        if (overviewHeadlineLabel == null) {
            return;
        }
        String symbol = activeSymbol.isBlank() ? "(none)" : activeSymbol;
        QuoteSnapshot snapshot = latestQuoteSnapshot;
        overviewHeadlineLabel.setText(" " + LocalDateTime.now().format(OVERVIEW_TIMESTAMP_FORMAT)
                + "   •    " + symbol
                + "   •    " + selectedSession().label());

        QuoteSnapshot.PriceState state = snapshot == null ? null : snapshot.priceState().orElse(null);
        if (state == QuoteSnapshot.PriceState.UP) {
            overviewSignalLabel.setText(" Bullish day");
            setOverviewSignalClass(true, false);
        } else if (state == QuoteSnapshot.PriceState.DOWN) {
            overviewSignalLabel.setText(" Bearish day");
            setOverviewSignalClass(false, true);
        } else {
            overviewSignalLabel.setText(" Neutral / waiting for day-change signal");
            setOverviewSignalClass(false, false);
        }

        overviewQuoteStatsLabel.setText(quoteStatsLine(snapshot));
        overviewIntradayStatsLabel.setText(intradayStatsLine());
        overviewHistoricalStatsLabel.setText(historicalStatsLine());
        overviewIndicatorStatsLabel.setText(indicatorStatsLine());
        overviewComputedStatsLabel.setText(computedStatsLine());
    }

    private String quoteStatsLine(QuoteSnapshot snapshot) {
        if (snapshot == null) {
            return " Quote: waiting for quote data.";
        }
        return " Last "
                + formatBigDecimal(snapshot.lastPrice())
                + "  |  Δ Day "
                + formatSigned(snapshot.change())
                + " (" + formatSigned(snapshot.percentChange()) + "%)"
                + "  |  Open " + formatBigDecimal(snapshot.openPrice())
                + "  |  Prev Close " + formatBigDecimal(snapshot.closePrice())
                + "  |  Tick " + formatBigDecimal(snapshot.latestTickPrice())
                + "  |  Market " + (snapshot.marketOpen() ? "Open" : "Closed");
    }

    private String intradayStatsLine() {
        if (intradayPoints.isEmpty()) {
            return " Intraday: waiting for intraday candles.";
        }
        IntradayPoint first = intradayPoints.getFirst();
        IntradayPoint last = intradayPoints.getLast();
        double open = toDouble(first.open());
        double close = toDouble(last.close());
        double high = intradayPoints.stream().mapToDouble(point -> toDouble(point.high())).max().orElse(Double.NaN);
        double low = intradayPoints.stream().mapToDouble(point -> toDouble(point.low())).min().orElse(Double.NaN);
        long totalVolume = intradayPoints.stream().mapToLong(point -> point.volume() == null ? 0 : point.volume()).sum();
        double vwap = totalVolume == 0
                ? Double.NaN
                : intradayPoints.stream().mapToDouble(point -> toDouble(point.close()) * point.volume()).sum() / totalVolume;
        double move = close - open;
        double movePct = open == 0 ? Double.NaN : (move / open) * 100.0;
        return " Intraday: "
                + first.timestamp().format(INTRADAY_AXIS_FORMAT) + " → " + last.timestamp().format(INTRADAY_AXIS_FORMAT)
                + "  |  O " + formatDouble(open, 2)
                + " H " + formatDouble(high, 2)
                + " L " + formatDouble(low, 2)
                + " C " + formatDouble(close, 2)
                + "  |  Δ " + formatSigned(move, 2) + " (" + formatSigned(movePct, 2) + "%)"
                + "  |  Vol " + formatWhole(totalVolume)
                + "  |  VWAP " + formatDouble(vwap, 2);
    }

    private String historicalStatsLine() {
        if (historicalPoints.isEmpty()) {
            return " Historical: waiting for daily candles.";
        }
        DailyOhlcPoint first = historicalPoints.getFirst();
        DailyOhlcPoint last = historicalPoints.getLast();
        double firstClose = toDouble(first.close());
        double lastClose = toDouble(last.close());
        double rangeHigh = historicalPoints.stream().mapToDouble(point -> toDouble(point.high())).max().orElse(Double.NaN);
        double rangeLow = historicalPoints.stream().mapToDouble(point -> toDouble(point.low())).min().orElse(Double.NaN);
        long totalVolume = historicalPoints.stream().mapToLong(point -> point.volume() == null ? 0 : point.volume()).sum();
        double avgVolume = historicalPoints.isEmpty() ? Double.NaN : (double) totalVolume / historicalPoints.size();
        double move = lastClose - firstClose;
        double movePct = firstClose == 0 ? Double.NaN : (move / firstClose) * 100.0;
        return " Historical: "
                + first.date() + " → " + last.date()
                + "  |  Candles " + historicalPoints.size()
                + "  |  Range " + formatDouble(rangeLow, 2) + " - " + formatDouble(rangeHigh, 2)
                + "  |  Period Δ " + formatSigned(move, 2) + " (" + formatSigned(movePct, 2) + "%)"
                + "  |  Avg Vol " + formatWhole(Math.round(avgVolume));
    }

    private String indicatorStatsLine() {
        List<Double> closes = intradayPoints.isEmpty()
                ? historicalPoints.stream().map(point -> toDouble(point.close())).toList()
                : intradayPoints.stream().map(point -> toDouble(point.close())).toList();
        String source = intradayPoints.isEmpty() ? "historical" : "intraday";
        if (closes.size() < 2) {
            return " Indicators: waiting for enough candles.";
        }
        double[] closeSeries = closes.stream().mapToDouble(Double::doubleValue).toArray();
        double latest = closeSeries[closeSeries.length - 1];
        double latestRsi = latestValid(computeRsi(closes, 14));
        MacdSeries macd = computeMacd(closes, 12, 26, 9);
        double latestMacd = latestValid(macd.macd());
        double latestSignal = latestValid(macd.signal());
        double latestHist = latestValid(macd.histogram());
        IndicatorSeries bollinger = computeBollingerBands(closes, 20, 2.0);
        double upper = latestValid(bollinger.upper());
        double middle = latestValid(bollinger.middle());
        double lower = latestValid(bollinger.lower());
        String bandPosition = Double.isNaN(upper) || Double.isNaN(lower) || upper == lower
                ? "n/a"
                : formatDouble(((latest - lower) / (upper - lower)) * 100.0, 1) + "%";

        return " Indicators (" + source + "): "
                + "RSI14 " + formatDouble(latestRsi, 2)
                + "  |  MACD " + formatDouble(latestMacd, 3)
                + " / Sig " + formatDouble(latestSignal, 3)
                + " / Hist " + formatDouble(latestHist, 3)
                + "  |  BB20 U/M/L "
                + formatDouble(upper, 2) + " / "
                + formatDouble(middle, 2) + " / "
                + formatDouble(lower, 2)
                + "  |  Band Pos " + bandPosition;
    }

    private String computedStatsLine() {
        List<Double> closes = historicalPoints.stream().map(point -> toDouble(point.close())).toList();
        if (closes.size() < 2) {
            return " Computed: waiting for enough historical points.";
        }
        double[] closeSeries = closes.stream().mapToDouble(Double::doubleValue).toArray();
        double sma20 = simpleMovingAverage(closeSeries, 20);
        double sma50 = simpleMovingAverage(closeSeries, 50);
        double ema20 = latestValid(computeEma(closeSeries, 20));
        double momentum5 = rateOfChange(closeSeries, 5);
        double momentum20 = rateOfChange(closeSeries, 20);
        double volatility = dailyReturnStdDev(closeSeries) * 100.0;
        double atr14 = atr14(historicalPoints);

        return " Computed: "
                + "SMA20 " + formatDouble(sma20, 2)
                + "  |  SMA50 " + formatDouble(sma50, 2)
                + "  |  EMA20 " + formatDouble(ema20, 2)
                + "  |  ROC5 " + formatSigned(momentum5, 2) + "%"
                + "  |  ROC20 " + formatSigned(momentum20, 2) + "%"
                + "  |  Volatility σ(daily) " + formatDouble(volatility, 2) + "%"
                + "  |  ATR14 " + formatDouble(atr14, 2);
    }

    private void setOverviewSignalClass(boolean up, boolean down) {
        overviewSignalLabel.getStyleClass().removeAll("overview-up", "overview-down");
        if (up) {
            overviewSignalLabel.getStyleClass().add("overview-up");
            return;
        }
        if (down) {
            overviewSignalLabel.getStyleClass().add("overview-down");
        }
    }

    private double toDouble(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }

    private double latestValid(double[] values) {
        for (int index = values.length - 1; index >= 0; index--) {
            if (!Double.isNaN(values[index])) {
                return values[index];
            }
        }
        return Double.NaN;
    }

    private double simpleMovingAverage(double[] values, int period) {
        if (values.length < period) {
            return Double.NaN;
        }
        double sum = 0;
        for (int index = values.length - period; index < values.length; index++) {
            sum += values[index];
        }
        return sum / period;
    }

    private double rateOfChange(double[] values, int period) {
        if (values.length <= period) {
            return Double.NaN;
        }
        double current = values[values.length - 1];
        double previous = values[values.length - 1 - period];
        if (previous == 0) {
            return Double.NaN;
        }
        return ((current - previous) / previous) * 100.0;
    }

    private double dailyReturnStdDev(double[] closes) {
        if (closes.length < 3) {
            return Double.NaN;
        }
        double[] returns = new double[closes.length - 1];
        for (int index = 1; index < closes.length; index++) {
            if (closes[index - 1] == 0) {
                return Double.NaN;
            }
            returns[index - 1] = (closes[index] - closes[index - 1]) / closes[index - 1];
        }
        double mean = Arrays.stream(returns).average().orElse(0);
        double variance = Arrays.stream(returns).map(value -> Math.pow(value - mean, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }

    private double atr14(List<DailyOhlcPoint> points) {
        if (points.size() < 15) {
            return Double.NaN;
        }
        int start = points.size() - 14;
        double sum = 0;
        for (int index = start; index < points.size(); index++) {
            DailyOhlcPoint current = points.get(index);
            double high = toDouble(current.high());
            double low = toDouble(current.low());
            double prevClose = toDouble(points.get(index - 1).close());
            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            sum += tr;
        }
        return sum / 14.0;
    }

    private String formatBigDecimal(BigDecimal value) {
        if (value == null) {
            return "n/a";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatSigned(BigDecimal value) {
        if (value == null) {
            return "n/a";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        String text = scaled.toPlainString();
        return text.startsWith("-") ? text : "+" + text;
    }

    private String formatDouble(double value, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "n/a";
        }
        BigDecimal scaled = BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
        return scaled.toPlainString();
    }

    private String formatSigned(double value, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "n/a";
        }
        String text = formatDouble(value, scale);
        return text.startsWith("-") ? text : "+" + text;
    }

    private String formatWhole(long value) {
        return String.format("%,d", value);
    }

    private void hideRetry() {
        retryIntradayButton.setVisible(false);
        retryIntradayButton.setManaged(false);
    }

    private void showRetry() {
        retryIntradayButton.setVisible(true);
        retryIntradayButton.setManaged(true);
    }

    private boolean isActiveSymbol(String symbol) {
        return symbol != null && symbol.equalsIgnoreCase(activeSymbol);
    }

    private IntradaySession selectedSession() {
        IntradaySession selected = sessionChoice.getValue();
        if (selected == null) {
            return IntradaySession.REGULAR;
        }
        if (selected == IntradaySession.EXTENDED && !extendedHoursSupported) {
            return IntradaySession.REGULAR;
        }
        return selected;
    }

    private void configureSessionChoice() {
        sessionChoice.setItems(FXCollections.observableArrayList(IntradaySession.values()));
        StringConverter<IntradaySession> converter = new StringConverter<>() {
            @Override
            public String toString(IntradaySession session) {
                return session == null ? "" : session.label();
            }

            @Override
            public IntradaySession fromString(String string) {
                return IntradaySession.REGULAR;
            }
        };
        sessionChoice.setConverter(converter);
        sessionChoice.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(IntradaySession session, boolean empty) {
                super.updateItem(session, empty);
                if (empty || session == null) {
                    setText(null);
                    setDisable(false);
                    return;
                }
                boolean disableExtended = session == IntradaySession.EXTENDED && !extendedHoursSupported;
                setDisable(disableExtended);
                setText(disableExtended ? "Extended Hours (Unavailable)" : session.label());
            }
        });
        sessionChoice.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(IntradaySession session, boolean empty) {
                super.updateItem(session, empty);
                if (empty || session == null) {
                    setText(null);
                    return;
                }
                if (session == IntradaySession.EXTENDED && !extendedHoursSupported) {
                    setText("Extended Hours (Unavailable)");
                    return;
                }
                setText(session.label());
            }
        });
        sessionChoice.setValue(IntradaySession.REGULAR);
    }

    private void configureIndicatorControls() {
        syncIndicatorControls();
    }

    private void syncIndicatorControls() {
        suppressIndicatorHandler = true;
        historicalRsiToggle.setSelected(rsiEnabled);
        historicalMacdToggle.setSelected(macdEnabled);
        historicalBollingerToggle.setSelected(bollingerEnabled);
        intradayRsiToggle.setSelected(rsiEnabled);
        intradayMacdToggle.setSelected(macdEnabled);
        intradayBollingerToggle.setSelected(bollingerEnabled);
        suppressIndicatorHandler = false;
    }

    private void configureChartTooltipDelays() {
        intradayTooltipDelay.setOnFinished(event -> {
            String tooltipText = buildIntradayTooltipText(intradayHoverCanvasX);
            if (tooltipText == null) {
                intradayTooltip.hide();
                return;
            }
            intradayTooltip.setText(tooltipText);
            intradayTooltip.show(intradayChartCanvas, intradayHoverScreenX + 12, intradayHoverScreenY + 12);
        });
        historicalTooltipDelay.setOnFinished(event -> {
            String tooltipText = buildHistoricalTooltipText(historicalHoverCanvasX);
            if (tooltipText == null) {
                historicalTooltip.hide();
                return;
            }
            historicalTooltip.setText(tooltipText);
            historicalTooltip.show(historicalChartCanvas, historicalHoverScreenX + 12, historicalHoverScreenY + 12);
        });
    }

    private void configureIntradayCanvas() {
        StackPane container = (StackPane) intradayChartCanvas.getParent();
        container.widthProperty().addListener((obs, oldValue, newValue) ->
                resizeCanvas(intradayChartCanvas, container, this::redrawIntradayChart));
        container.heightProperty().addListener((obs, oldValue, newValue) ->
                resizeCanvas(intradayChartCanvas, container, this::redrawIntradayChart));
        Platform.runLater(() -> resizeCanvas(intradayChartCanvas, container, this::redrawIntradayChart));
        intradayChartCanvas.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY || !isIntradayPannable()) {
                return;
            }
            intradayPanActive = true;
            intradayPanAnchorX = event.getX();
            intradayPanAnchorStart = intradayViewportStart;
            updateIntradayCursor();
            event.consume();
        });
        intradayChartCanvas.setOnMouseDragged(event -> {
            if (!intradayPanActive || intradayPoints.isEmpty()) {
                return;
            }
            double slotWidth = currentIntradaySlotWidth();
            if (slotWidth <= 0) {
                return;
            }
            int deltaSlots = (int) Math.round((intradayPanAnchorX - event.getX()) / slotWidth);
            int nextStart = clampIntradayViewportStart(
                    intradayPanAnchorStart + deltaSlots,
                    intradayPoints.size(),
                    intradayViewportSize);
            if (nextStart != intradayViewportStart) {
                intradayViewportStart = nextStart;
                redrawIntradayChart();
            }
            event.consume();
        });
        intradayChartCanvas.setOnScroll(event -> {
            if (intradayPoints.isEmpty()) {
                return;
            }
            if (Math.abs(event.getDeltaY()) >= Math.abs(event.getDeltaX())) {
                zoomIntradayViewport(event.getX(), event.getDeltaY());
            } else {
                panIntradayViewport(event.getDeltaX());
            }
            event.consume();
        });
        intradayChartCanvas.setOnMouseMoved(event -> {
            intradayTooltip.hide();
            intradayHoverCanvasX = event.getX();
            intradayHoverScreenX = event.getScreenX();
            intradayHoverScreenY = event.getScreenY();
            intradayTooltipDelay.playFromStart();
        });
        intradayChartCanvas.setOnMouseReleased(event -> endIntradayPan());
        intradayChartCanvas.setOnMouseExited(event -> {
            endIntradayPan();
            intradayTooltipDelay.stop();
            intradayTooltip.hide();
        });
        clearIntradayChart();
    }

    private void configureHistoricalControls() {
        LocalDate today = LocalDate.now();
        historicalEndDatePicker.setValue(today);
        historicalStartDatePicker.setValue(HistoricalRangePreset.DAYS_30.toDateRange(today).startDate());
        symbolInput.textProperty().addListener((obs, oldValue, newValue) -> updateHistoricalInputState());
        historicalStartDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> updateHistoricalInputState());
        historicalEndDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> updateHistoricalInputState());
        historicalStatusLabel.setText("Enter a symbol and choose a range.");
        showHistoricalEmptyMessage("Enter a symbol to load historical daily candles.");
        hideHistoricalRetry();
        hideHistoricalErrorBanner();
        hideHistoricalValidationMessage();
        updateHistoricalInputState();
    }

    private void configureHistoricalCanvas() {
        StackPane container = (StackPane) historicalChartCanvas.getParent();
        container.widthProperty().addListener((obs, oldValue, newValue) ->
                resizeCanvas(historicalChartCanvas, container, this::redrawHistoricalChart));
        container.heightProperty().addListener((obs, oldValue, newValue) ->
                resizeCanvas(historicalChartCanvas, container, this::redrawHistoricalChart));
        Platform.runLater(() -> resizeCanvas(historicalChartCanvas, container, this::redrawHistoricalChart));
        historicalChartCanvas.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY || !isHistoricalPannable()) {
                return;
            }
            historicalPanActive = true;
            historicalPanAnchorX = event.getX();
            historicalPanAnchorStart = historicalViewportStart;
            event.consume();
        });
        historicalChartCanvas.setOnMouseDragged(event -> {
            if (!historicalPanActive || historicalPoints.isEmpty() || historicalViewportSize <= 0) {
                return;
            }
            double slotWidth = currentHistoricalSlotWidth();
            if (slotWidth <= 0) {
                return;
            }
            int deltaSlots = (int) Math.round((historicalPanAnchorX - event.getX()) / slotWidth);
            int nextStart = clampHistoricalViewportStart(
                    historicalPanAnchorStart + deltaSlots,
                    historicalPoints.size(),
                    historicalViewportSize);
            if (nextStart != historicalViewportStart) {
                historicalViewportStart = nextStart;
                redrawHistoricalChart();
            }
            event.consume();
        });
        historicalChartCanvas.setOnScroll(event -> {
            if (historicalPoints.isEmpty()) {
                return;
            }
            if (Math.abs(event.getDeltaY()) >= Math.abs(event.getDeltaX())) {
                zoomHistoricalViewport(event.getX(), event.getDeltaY());
            } else {
                panHistoricalViewport(event.getDeltaX());
            }
            event.consume();
        });
        historicalChartCanvas.setOnMouseMoved(event -> {
            historicalTooltip.hide();
            historicalHoverCanvasX = event.getX();
            historicalHoverScreenX = event.getScreenX();
            historicalHoverScreenY = event.getScreenY();
            historicalTooltipDelay.playFromStart();
        });
        historicalChartCanvas.setOnMouseReleased(event -> historicalPanActive = false);
        historicalChartCanvas.setOnMouseExited(event -> {
            historicalPanActive = false;
            historicalTooltipDelay.stop();
            historicalTooltip.hide();
        });
        clearHistoricalChart();
    }

    private void applyHistoricalPreset(HistoricalRangePreset preset) {
        historicalPreset = preset;
        String symbol = normalizedHistoricalSymbol();
        if (symbol.isEmpty()) {
            historicalStatusLabel.setText("Symbol is required.");
            showHistoricalValidationMessage("Enter a symbol to fetch historical data.");
            showHistoricalEmptyMessage("Enter a symbol to load historical daily candles.");
            updateOverviewSummary();
            return;
        }
        hideHistoricalValidationMessage();
        boolean symbolChanged = syncActiveSymbol(symbol);
        if (symbolChanged) {
            refreshOverviewAndIntraday(symbol);
        }
        requestHistoricalPreset(symbol, preset);
    }

    private void requestAllContextsForSymbol(String symbol) {
        syncActiveSymbol(symbol);
        refreshOverviewAndIntraday(symbol);
        requestHistoricalPreset(symbol, historicalPreset);
    }

    private boolean syncActiveSymbol(String symbol) {
        String normalized = normalizeSymbol(symbol);
        boolean changed = !normalized.equalsIgnoreCase(activeSymbol);
        activeSymbol = normalized;
        symbolInput.setText(normalized);
        updateHistoricalInputState();
        return changed;
    }

    private void refreshOverviewAndIntraday(String symbol) {
        resetSessionAvailability();
        clearQuoteState();
        clearIntradayChart();
        hideIntradayEmptyMessage();
        setIntradayChartVisible(true);
        hideRetry();
        intradayViewState = IntradayViewState.LOADING;
        statusLabel.setText("Fetching intraday for " + symbol + "...");
        quoteService.requestQuote(symbol);
        intradayService.requestIntraday(symbol, selectedSession());
        updateOverviewSummary();
    }

    private void requestHistoricalPreset(String symbol, HistoricalRangePreset preset) {
        historicalStatusLabel.setText("Loading " + presetLabel(preset) + " daily candles for " + symbol + "...");
        hideHistoricalEmptyMessage();
        hideHistoricalErrorBanner();
        hideHistoricalRetry();
        hideHistoricalValidationMessage();
        clearHistoricalChart();
        historicalDailyService.requestDailyHistory(symbol, preset);
        LocalDate today = LocalDate.now();
        historicalEndDatePicker.setValue(today);
        historicalStartDatePicker.setValue(preset.toDateRange(today).startDate());
        updateOverviewSummary();
    }

    private String presetLabel(HistoricalRangePreset preset) {
        return switch (preset) {
            case DAYS_30 -> "30D";
            case DAYS_90 -> "90D";
            case DAYS_120 -> "120D";
        };
    }

    private String normalizedHistoricalSymbol() {
        return normalizeSymbol(symbolInput.getText());
    }

    private void redrawHistoricalChart() {
        if (historicalPoints.isEmpty()) {
            clearHistoricalChart();
            return;
        }
        renderHistoricalCandles(historicalPoints);
    }

    private void redrawIntradayChart() {
        if (intradayPoints.isEmpty()) {
            clearIntradayChart();
            return;
        }
        renderIntradayCandles(intradayPoints);
    }

    private void renderHistoricalCandles(List<DailyOhlcPoint> points) {
        if (points == null || points.isEmpty()) {
            clearHistoricalChart();
            return;
        }

        double width = historicalChartCanvas.getWidth();
        double height = historicalChartCanvas.getHeight();
        if (!hasRenderableSize(width, height)) {
            return;
        }
        GraphicsContext graphics = historicalChartCanvas.getGraphicsContext2D();
        graphics.setFill(CHART_BACKGROUND_COLOR);
        graphics.fillRect(0, 0, width, height);

        ChartLayout layout = createChartLayout(width, height, rsiEnabled, macdEnabled);
        WindowRange historicalWindow = resolveHistoricalWindow(points.size(), layout.pricePanel().width());
        List<DailyOhlcPoint> visiblePoints = points.subList(
                historicalWindow.startInclusive(),
                historicalWindow.endExclusive());
        historicalVisiblePoints = List.copyOf(visiblePoints);
        List<Double> closes = visiblePoints.stream().map(point -> point.close().doubleValue()).toList();
        IndicatorSeries bollinger = computeBollingerBands(closes, 20, 2.0);
        historicalVisibleBollingerUpper = bollinger.upper();
        historicalVisibleBollingerMiddle = bollinger.middle();
        historicalVisibleBollingerLower = bollinger.lower();
        historicalVisibleRsi = computeRsi(closes, 14);
        String firstLabel = visiblePoints.getFirst().date().format(DATE_AXIS_FORMAT);
        String lastLabel = visiblePoints.getLast().date().format(DATE_AXIS_FORMAT);
        drawPricePanel(graphics, layout, visiblePoints.size(), firstLabel, lastLabel,
                visiblePoints.stream().mapToDouble(point -> point.open().doubleValue()).toArray(),
                visiblePoints.stream().mapToDouble(point -> point.high().doubleValue()).toArray(),
                visiblePoints.stream().mapToDouble(point -> point.low().doubleValue()).toArray(),
                visiblePoints.stream().mapToDouble(point -> point.close().doubleValue()).toArray(),
                bollingerEnabled ? bollinger : null);

        if (rsiEnabled) {
            drawRsiPanel(graphics, layout, visiblePoints.size(), historicalVisibleRsi, firstLabel, lastLabel);
        }
        if (macdEnabled) {
            MacdSeries macdSeries = computeMacd(closes, 12, 26, 9);
            historicalVisibleMacd = macdSeries.macd();
            historicalVisibleMacdSignal = macdSeries.signal();
            historicalVisibleMacdHistogram = macdSeries.histogram();
            drawMacdPanel(graphics, layout, visiblePoints.size(), macdSeries, firstLabel, lastLabel);
        } else {
            historicalVisibleMacd = new double[0];
            historicalVisibleMacdSignal = new double[0];
            historicalVisibleMacdHistogram = new double[0];
        }
    }

    private ChartLayout createChartLayout(double width, double height, boolean showRsi, boolean showMacd) {
        double leftPad = CHART_LEFT_PAD;
        double rightPad = CHART_RIGHT_PAD;
        double topPad = 16;
        double bottomPad = 28;
        double panelGap = 8;
        int panelCount = 1 + (showRsi ? 1 : 0) + (showMacd ? 1 : 0);
        double availableHeight = Math.max(1, height - topPad - bottomPad - (panelCount - 1) * panelGap);
        double baseUnit = availableHeight / (showRsi || showMacd ? 4.0 : 1.0);
        double priceHeight = showRsi || showMacd ? baseUnit * 2 : availableHeight;
        double indicatorHeight = showRsi || showMacd ? baseUnit : 0;
        double panelWidth = Math.max(1, width - leftPad - rightPad);

        PanelBounds pricePanel = new PanelBounds(leftPad, topPad, panelWidth, Math.max(1, priceHeight));
        PanelBounds rsiPanel = null;
        PanelBounds macdPanel = null;
        double nextY = topPad + pricePanel.height() + panelGap;
        if (showRsi) {
            rsiPanel = new PanelBounds(leftPad, nextY, panelWidth, Math.max(1, indicatorHeight));
            nextY += rsiPanel.height() + panelGap;
        }
        if (showMacd) {
            macdPanel = new PanelBounds(leftPad, nextY, panelWidth, Math.max(1, indicatorHeight));
        }
        return new ChartLayout(pricePanel, rsiPanel, macdPanel);
    }

    private void drawPricePanel(GraphicsContext graphics,
                                ChartLayout layout,
                                int pointCount,
                                String firstLabel,
                                String lastLabel,
                                double[] opens,
                                double[] highs,
                                double[] lows,
                                double[] closes,
                                IndicatorSeries bollinger) {
        if (pointCount == 0) {
            return;
        }
        PanelBounds panel = layout.pricePanel();
        double minLow = Arrays.stream(lows).min().orElse(0);
        double maxHigh = Arrays.stream(highs).max().orElse(0);
        if (bollinger != null) {
            double bandMin = minValid(bollinger.lower(), Double.NaN);
            double bandMax = maxValid(bollinger.upper(), Double.NaN);
            if (!Double.isNaN(bandMin)) {
                minLow = Math.min(minLow, bandMin);
            }
            if (!Double.isNaN(bandMax)) {
                maxHigh = Math.max(maxHigh, bandMax);
            }
        }
        if (maxHigh <= minLow) {
            maxHigh = minLow + 1;
        }
        double priceRange = maxHigh - minLow;

        double slotWidth = panel.width() / pointCount;
        double bodyWidth = Math.max(2, Math.min(14, slotWidth * 0.6));
        for (int index = 0; index < pointCount; index++) {
            double candleCenterX = panel.x() + (index + 0.5) * slotWidth;
            double openY = panel.y() + ((maxHigh - opens[index]) / priceRange) * panel.height();
            double closeY = panel.y() + ((maxHigh - closes[index]) / priceRange) * panel.height();
            double highY = panel.y() + ((maxHigh - highs[index]) / priceRange) * panel.height();
            double lowY = panel.y() + ((maxHigh - lows[index]) / priceRange) * panel.height();

            boolean upCandle = closes[index] >= opens[index];
            graphics.setStroke(upCandle ? CHART_POSITIVE_COLOR : CHART_NEGATIVE_COLOR);
            graphics.strokeLine(candleCenterX, highY, candleCenterX, lowY);

            double bodyTop = Math.min(openY, closeY);
            double bodyHeight = Math.max(1.2, Math.abs(closeY - openY));
            graphics.setFill(upCandle ? CHART_POSITIVE_COLOR : CHART_NEGATIVE_COLOR);
            graphics.fillRect(candleCenterX - bodyWidth / 2, bodyTop, bodyWidth, bodyHeight);
        }

        if (bollinger != null) {
            drawSeriesLine(graphics, panel, bollinger.upper(), minLow, maxHigh, BOLLINGER_COLOR, 1.2);
            drawSeriesLine(graphics, panel, bollinger.middle(), minLow, maxHigh, BOLLINGER_MID_COLOR, 1.0);
            drawSeriesLine(graphics, panel, bollinger.lower(), minLow, maxHigh, BOLLINGER_COLOR, 1.2);
        }

        drawPanelAxes(graphics, panel);
        graphics.setFill(CHART_LABEL_COLOR);
        graphics.fillText(String.format("%.2f", maxHigh), 8, panel.y() + 8);
        graphics.fillText(String.format("%.2f", minLow), 8, panel.y() + panel.height());
        if (!layout.hasIndicatorPanels()) {
            drawXAxisLabels(graphics, panel, firstLabel, lastLabel);
        }
    }

    private void drawRsiPanel(GraphicsContext graphics,
                              ChartLayout layout,
                              int pointCount,
                              double[] rsi,
                              String firstLabel,
                              String lastLabel) {
        PanelBounds panel = layout.rsiPanel();
        if (panel == null || pointCount == 0) {
            return;
        }
        drawPanelAxes(graphics, panel);
        drawHorizontalGuide(graphics, panel, 30, 0, 100);
        drawHorizontalGuide(graphics, panel, 70, 0, 100);
        drawSeriesLine(graphics, panel, rsi, 0, 100, RSI_COLOR, 1.2);
        graphics.setFill(CHART_LABEL_COLOR);
        graphics.fillText("RSI", 8, panel.y() + 12);
        graphics.fillText("70", 8, yForValue(panel, 70, 0, 100));
        graphics.fillText("30", 8, yForValue(panel, 30, 0, 100));
        if (!layout.hasMacdPanel()) {
            drawXAxisLabels(graphics, panel, firstLabel, lastLabel);
        }
    }

    private void drawMacdPanel(GraphicsContext graphics,
                               ChartLayout layout,
                               int pointCount,
                               MacdSeries macdSeries,
                               String firstLabel,
                               String lastLabel) {
        PanelBounds panel = layout.macdPanel();
        if (panel == null || pointCount == 0) {
            return;
        }
        double minValue = minValid(new double[][]{
                macdSeries.histogram(),
                macdSeries.macd(),
                macdSeries.signal()
        }, 0);
        double maxValue = maxValid(new double[][]{
                macdSeries.histogram(),
                macdSeries.macd(),
                macdSeries.signal()
        }, 0);
        if (maxValue <= minValue) {
            maxValue = minValue + 1;
        }
        drawPanelAxes(graphics, panel);

        double slotWidth = panel.width() / pointCount;
        double zeroY = yForValue(panel, 0, minValue, maxValue);
        graphics.setStroke(RSI_GUIDE_COLOR);
        graphics.strokeLine(panel.x(), zeroY, panel.x() + panel.width(), zeroY);
        for (int index = 0; index < pointCount; index++) {
            double value = macdSeries.histogram()[index];
            if (Double.isNaN(value)) {
                continue;
            }
            double centerX = panel.x() + (index + 0.5) * slotWidth;
            double valueY = yForValue(panel, value, minValue, maxValue);
            double barTop = Math.min(zeroY, valueY);
            double barHeight = Math.max(1, Math.abs(valueY - zeroY));
            graphics.setFill(value >= 0 ? CHART_POSITIVE_COLOR : CHART_NEGATIVE_COLOR);
            graphics.fillRect(centerX - Math.max(1, slotWidth * 0.35), barTop, Math.max(1.5, slotWidth * 0.7), barHeight);
        }

        drawSeriesLine(graphics, panel, macdSeries.macd(), minValue, maxValue, MACD_COLOR, 1.2);
        drawSeriesLine(graphics, panel, macdSeries.signal(), minValue, maxValue, MACD_SIGNAL_COLOR, 1.2);

        graphics.setFill(CHART_LABEL_COLOR);
        graphics.fillText("MACD", 8, panel.y() + 12);
        drawXAxisLabels(graphics, panel, firstLabel, lastLabel);
    }

    private void drawPanelAxes(GraphicsContext graphics, PanelBounds panel) {
        graphics.setStroke(CHART_AXIS_COLOR);
        graphics.strokeLine(panel.x(), panel.y() + panel.height(), panel.x() + panel.width(), panel.y() + panel.height());
        graphics.strokeLine(panel.x(), panel.y(), panel.x(), panel.y() + panel.height());
    }

    private void drawXAxisLabels(GraphicsContext graphics, PanelBounds panel, String firstLabel, String lastLabel) {
        graphics.setFill(CHART_LABEL_COLOR);
        graphics.fillText(firstLabel, panel.x(), panel.y() + panel.height() + 18);
        graphics.fillText(lastLabel, panel.x() + panel.width() - 36, panel.y() + panel.height() + 18);
    }

    private void drawSeriesLine(GraphicsContext graphics,
                                PanelBounds panel,
                                double[] values,
                                double minValue,
                                double maxValue,
                                Color color,
                                double lineWidth) {
        if (values == null || values.length == 0) {
            return;
        }
        graphics.setStroke(color);
        graphics.setLineWidth(lineWidth);
        double slotWidth = panel.width() / values.length;
        boolean segmentOpen = false;
        for (int index = 0; index < values.length; index++) {
            double value = values[index];
            if (Double.isNaN(value)) {
                if (segmentOpen) {
                    graphics.stroke();
                }
                segmentOpen = false;
                continue;
            }
            double x = panel.x() + (index + 0.5) * slotWidth;
            double y = yForValue(panel, value, minValue, maxValue);
            if (!segmentOpen) {
                graphics.beginPath();
                graphics.moveTo(x, y);
                segmentOpen = true;
            } else {
                graphics.lineTo(x, y);
            }
        }
        if (segmentOpen) {
            graphics.stroke();
        }
        graphics.setLineWidth(1.0);
    }

    private void drawHorizontalGuide(GraphicsContext graphics, PanelBounds panel, double value, double minValue, double maxValue) {
        graphics.setStroke(RSI_GUIDE_COLOR);
        double y = yForValue(panel, value, minValue, maxValue);
        graphics.strokeLine(panel.x(), y, panel.x() + panel.width(), y);
    }

    private double yForValue(PanelBounds panel, double value, double minValue, double maxValue) {
        double clampedMax = maxValue <= minValue ? minValue + 1 : maxValue;
        return panel.y() + ((clampedMax - value) / (clampedMax - minValue)) * panel.height();
    }

    private IndicatorSeries computeBollingerBands(List<Double> closes, int period, double multiplier) {
        int size = closes.size();
        double[] upper = nanSeries(size);
        double[] middle = nanSeries(size);
        double[] lower = nanSeries(size);
        if (size < period || period <= 1) {
            return new IndicatorSeries(upper, middle, lower);
        }

        double rollingSum = 0;
        double rollingSquares = 0;
        for (int index = 0; index < size; index++) {
            double value = closes.get(index);
            rollingSum += value;
            rollingSquares += value * value;
            if (index >= period) {
                double dropped = closes.get(index - period);
                rollingSum -= dropped;
                rollingSquares -= dropped * dropped;
            }
            if (index >= period - 1) {
                double mean = rollingSum / period;
                double variance = Math.max(0, (rollingSquares / period) - (mean * mean));
                double stdDev = Math.sqrt(variance);
                middle[index] = mean;
                upper[index] = mean + (multiplier * stdDev);
                lower[index] = mean - (multiplier * stdDev);
            }
        }
        return new IndicatorSeries(upper, middle, lower);
    }

    private double[] computeRsi(List<Double> closes, int period) {
        int size = closes.size();
        double[] rsi = nanSeries(size);
        if (size <= period || period <= 0) {
            return rsi;
        }

        double gainSum = 0;
        double lossSum = 0;
        for (int index = 1; index <= period; index++) {
            double delta = closes.get(index) - closes.get(index - 1);
            gainSum += Math.max(0, delta);
            lossSum += Math.max(0, -delta);
        }
        double averageGain = gainSum / period;
        double averageLoss = lossSum / period;
        rsi[period] = toRsi(averageGain, averageLoss);

        for (int index = period + 1; index < size; index++) {
            double delta = closes.get(index) - closes.get(index - 1);
            double gain = Math.max(0, delta);
            double loss = Math.max(0, -delta);
            averageGain = ((averageGain * (period - 1)) + gain) / period;
            averageLoss = ((averageLoss * (period - 1)) + loss) / period;
            rsi[index] = toRsi(averageGain, averageLoss);
        }
        return rsi;
    }

    private double toRsi(double averageGain, double averageLoss) {
        if (averageLoss == 0) {
            return 100;
        }
        double relativeStrength = averageGain / averageLoss;
        return 100 - (100 / (1 + relativeStrength));
    }

    private MacdSeries computeMacd(List<Double> closes, int fastPeriod, int slowPeriod, int signalPeriod) {
        double[] closeArray = closes.stream().mapToDouble(Double::doubleValue).toArray();
        double[] fastEma = computeEma(closeArray, fastPeriod);
        double[] slowEma = computeEma(closeArray, slowPeriod);
        double[] macd = nanSeries(closeArray.length);
        for (int index = 0; index < closeArray.length; index++) {
            if (Double.isNaN(fastEma[index]) || Double.isNaN(slowEma[index])) {
                continue;
            }
            macd[index] = fastEma[index] - slowEma[index];
        }
        double[] signal = computeEma(macd, signalPeriod);
        double[] histogram = nanSeries(closeArray.length);
        for (int index = 0; index < closeArray.length; index++) {
            if (Double.isNaN(macd[index]) || Double.isNaN(signal[index])) {
                continue;
            }
            histogram[index] = macd[index] - signal[index];
        }
        return new MacdSeries(macd, signal, histogram);
    }

    private double[] computeEma(double[] values, int period) {
        double[] ema = nanSeries(values.length);
        if (values.length < period || period <= 0) {
            return ema;
        }
        int start = 0;
        while (start < values.length && Double.isNaN(values[start])) {
            start++;
        }
        if (values.length - start < period) {
            return ema;
        }
        double seed = 0;
        for (int index = start; index < start + period; index++) {
            if (Double.isNaN(values[index])) {
                return ema;
            }
            seed += values[index];
        }
        int seedIndex = start + period - 1;
        double previous = seed / period;
        ema[seedIndex] = previous;
        double alpha = 2.0 / (period + 1);
        for (int index = seedIndex + 1; index < values.length; index++) {
            if (Double.isNaN(values[index])) {
                continue;
            }
            previous = ((values[index] - previous) * alpha) + previous;
            ema[index] = previous;
        }
        return ema;
    }

    private double[] nanSeries(int size) {
        double[] values = new double[size];
        Arrays.fill(values, Double.NaN);
        return values;
    }

    private double minValid(double[] values, double fallback) {
        return minValid(new double[][]{values}, fallback);
    }

    private double minValid(double[]... arrays) {
        return minValid(arrays, 0);
    }

    private double minValid(double[][] arrays, double fallback) {
        double min = Double.POSITIVE_INFINITY;
        for (double[] array : arrays) {
            for (double value : array) {
                if (!Double.isNaN(value)) {
                    min = Math.min(min, value);
                }
            }
        }
        return min == Double.POSITIVE_INFINITY ? fallback : min;
    }

    private double maxValid(double[] values, double fallback) {
        return maxValid(new double[][]{values}, fallback);
    }

    private double maxValid(double[]... arrays) {
        return maxValid(arrays, 0);
    }

    private double maxValid(double[][] arrays, double fallback) {
        double max = Double.NEGATIVE_INFINITY;
        for (double[] array : arrays) {
            for (double value : array) {
                if (!Double.isNaN(value)) {
                    max = Math.max(max, value);
                }
            }
        }
        return max == Double.NEGATIVE_INFINITY ? fallback : max;
    }

    private record PanelBounds(double x, double y, double width, double height) {
    }

    private record ChartLayout(PanelBounds pricePanel, PanelBounds rsiPanel, PanelBounds macdPanel) {
        private boolean hasIndicatorPanels() {
            return rsiPanel != null || macdPanel != null;
        }

        private boolean hasMacdPanel() {
            return macdPanel != null;
        }
    }

    private record IndicatorSeries(double[] upper, double[] middle, double[] lower) {
    }

    private record MacdSeries(double[] macd, double[] signal, double[] histogram) {
    }

    private void clearHistoricalChart() {
        historicalPoints = List.of();
        resetHistoricalViewport();
        historicalPanActive = false;
        historicalVisiblePoints = List.of();
        historicalVisibleRsi = new double[0];
        historicalVisibleMacd = new double[0];
        historicalVisibleMacdSignal = new double[0];
        historicalVisibleMacdHistogram = new double[0];
        historicalVisibleBollingerUpper = new double[0];
        historicalVisibleBollingerMiddle = new double[0];
        historicalVisibleBollingerLower = new double[0];
        historicalTooltipDelay.stop();
        historicalTooltip.hide();
        if (!hasRenderableSize(historicalChartCanvas.getWidth(), historicalChartCanvas.getHeight())) {
            return;
        }
        GraphicsContext graphics = historicalChartCanvas.getGraphicsContext2D();
        graphics.setFill(CHART_BACKGROUND_COLOR);
        graphics.fillRect(0, 0, historicalChartCanvas.getWidth(), historicalChartCanvas.getHeight());
    }

    private void clearIntradayChart() {
        intradayPoints = List.of();
        intradayViewportStart = 0;
        intradayViewportSize = 0;
        intradayPanActive = false;
        intradayVisiblePoints = List.of();
        intradayVisibleRsi = new double[0];
        intradayVisibleMacd = new double[0];
        intradayVisibleMacdSignal = new double[0];
        intradayVisibleMacdHistogram = new double[0];
        intradayVisibleBollingerUpper = new double[0];
        intradayVisibleBollingerMiddle = new double[0];
        intradayVisibleBollingerLower = new double[0];
        intradayTooltipDelay.stop();
        intradayTooltip.hide();
        if (!hasRenderableSize(intradayChartCanvas.getWidth(), intradayChartCanvas.getHeight())) {
            updateIntradayCursor();
            return;
        }
        GraphicsContext graphics = intradayChartCanvas.getGraphicsContext2D();
        graphics.setFill(CHART_BACKGROUND_COLOR);
        graphics.fillRect(0, 0, intradayChartCanvas.getWidth(), intradayChartCanvas.getHeight());
        updateIntradayCursor();
    }

    private boolean hasRenderableSize(double width, double height) {
        return Double.isFinite(width) && Double.isFinite(height) && width > 1 && height > 1;
    }

    private void setIntradayChartVisible(boolean visible) {
        intradayChartCanvas.setVisible(visible);
        intradayChartCanvas.setManaged(visible);
    }

    private void showIntradayEmptyMessage(String message) {
        intradayEmptyLabel.setText(message);
        intradayEmptyLabel.setVisible(true);
        intradayEmptyLabel.setManaged(true);
    }

    private void hideIntradayEmptyMessage() {
        intradayEmptyLabel.setVisible(false);
        intradayEmptyLabel.setManaged(false);
    }

    private WindowRange resolveIntradayWindow(int totalPoints, double panelWidth) {
        int visiblePoints = resolveIntradayVisibleCount(totalPoints, panelWidth);
        int maxStart = Math.max(0, totalPoints - visiblePoints);
        if (intradayViewportSize <= 0 || intradayViewportSize > totalPoints) {
            intradayViewportStart = maxStart;
            intradayViewportSize = visiblePoints;
        } else {
            intradayViewportSize = Math.min(totalPoints, intradayViewportSize);
            intradayViewportStart = clampIntradayViewportStart(intradayViewportStart, totalPoints, intradayViewportSize);
        }
        return new WindowRange(intradayViewportStart, intradayViewportStart + intradayViewportSize);
    }

    private int resolveIntradayVisibleCount(int totalPoints, double panelWidth) {
        if (totalPoints <= 0) {
            return 0;
        }
        int byWidth = Math.max(1, (int) Math.floor(panelWidth / INTRADAY_TARGET_SLOT_WIDTH_PX));
        int target = Math.max(INTRADAY_MIN_VISIBLE_POINTS, byWidth);
        return Math.min(totalPoints, target);
    }

    private int clampIntradayViewportStart(int proposedStart, int totalPoints, int visiblePoints) {
        int maxStart = Math.max(0, totalPoints - Math.max(visiblePoints, 1));
        return Math.max(0, Math.min(proposedStart, maxStart));
    }

    private boolean isIntradayPannable() {
        return intradayViewportSize > 0 && intradayPoints.size() > intradayViewportSize;
    }

    private double currentIntradaySlotWidth() {
        double panelWidth = Math.max(1, intradayChartCanvas.getWidth() - CHART_LEFT_PAD - CHART_RIGHT_PAD);
        int visiblePoints = Math.max(1, intradayViewportSize);
        return panelWidth / visiblePoints;
    }

    private WindowRange resolveHistoricalWindow(int totalPoints, double panelWidth) {
        int visiblePoints = resolveHistoricalVisibleCount(totalPoints, panelWidth);
        int maxStart = Math.max(0, totalPoints - visiblePoints);
        if (historicalViewportSize <= 0 || historicalViewportSize > totalPoints) {
            historicalViewportStart = maxStart;
            historicalViewportSize = visiblePoints;
        } else {
            historicalViewportSize = Math.min(totalPoints, historicalViewportSize);
            historicalViewportStart = clampHistoricalViewportStart(
                    historicalViewportStart,
                    totalPoints,
                    historicalViewportSize);
        }
        return new WindowRange(historicalViewportStart, historicalViewportStart + historicalViewportSize);
    }

    private int resolveHistoricalVisibleCount(int totalPoints, double panelWidth) {
        if (totalPoints <= 0) {
            return 0;
        }
        int byWidth = Math.max(1, (int) Math.floor(panelWidth / INTRADAY_TARGET_SLOT_WIDTH_PX));
        return Math.min(totalPoints, Math.max(INTRADAY_MIN_VISIBLE_POINTS, byWidth));
    }

    private int clampHistoricalViewportStart(int proposedStart, int totalPoints, int visiblePoints) {
        int maxStart = Math.max(0, totalPoints - Math.max(visiblePoints, 1));
        return Math.max(0, Math.min(proposedStart, maxStart));
    }

    private boolean isHistoricalPannable() {
        return historicalViewportSize > 0 && historicalPoints.size() > historicalViewportSize;
    }

    private double currentHistoricalSlotWidth() {
        double panelWidth = Math.max(1, historicalChartCanvas.getWidth() - CHART_LEFT_PAD - CHART_RIGHT_PAD);
        int visiblePoints = Math.max(1, historicalViewportSize);
        return panelWidth / visiblePoints;
    }

    private void resetHistoricalViewport() {
        historicalViewportStart = 0;
        historicalViewportSize = 0;
    }

    private int toScrollSlots(double deltaY, double deltaX) {
        double effectiveDelta = Math.abs(deltaY) >= Math.abs(deltaX) ? deltaY : deltaX;
        if (Math.abs(effectiveDelta) < 0.5) {
            return 0;
        }
        int slots = Math.max(1, (int) Math.round(Math.abs(effectiveDelta) / 40.0));
        return (int) Math.signum(effectiveDelta) * slots;
    }

    private void resizeCanvas(Canvas canvas, StackPane container, Runnable redraw) {
        Insets insets = container.getInsets();
        double nextWidth = Math.max(1, container.getWidth() - insets.getLeft() - insets.getRight());
        double nextHeight = Math.max(1, container.getHeight() - insets.getTop() - insets.getBottom());
        if (Math.abs(canvas.getWidth() - nextWidth) > 0.5) {
            canvas.setWidth(nextWidth);
        }
        if (Math.abs(canvas.getHeight() - nextHeight) > 0.5) {
            canvas.setHeight(nextHeight);
        }
        redraw.run();
    }

    private void panIntradayViewport(double deltaX) {
        if (intradayViewportSize <= 0) {
            return;
        }
        int deltaSlots = toScrollSlots(0, deltaX);
        if (deltaSlots == 0) {
            return;
        }
        int nextStart = clampIntradayViewportStart(
                intradayViewportStart + deltaSlots,
                intradayPoints.size(),
                intradayViewportSize);
        if (nextStart != intradayViewportStart) {
            intradayViewportStart = nextStart;
            redrawIntradayChart();
        }
    }

    private void zoomIntradayViewport(double mouseX, double deltaY) {
        if (Math.abs(deltaY) < 0.5) {
            return;
        }
        if (intradayViewportSize <= 0) {
            intradayViewportSize = resolveIntradayVisibleCount(
                    intradayPoints.size(),
                    Math.max(1, intradayChartCanvas.getWidth() - CHART_LEFT_PAD - CHART_RIGHT_PAD));
        }
        int zoomSteps = Math.max(1, (int) Math.round(Math.abs(deltaY) / 40.0));
        int visibleChange = zoomSteps * 2;
        int direction = (int) Math.signum(deltaY);
        int nextVisible = direction > 0
                ? intradayViewportSize - visibleChange
                : intradayViewportSize + visibleChange;
        int minVisible = Math.min(CHART_MIN_ZOOM_VISIBLE_POINTS, intradayPoints.size());
        nextVisible = Math.max(minVisible, Math.min(nextVisible, intradayPoints.size()));
        if (nextVisible == intradayViewportSize) {
            return;
        }
        int anchorIndex = resolveAnchorIndex(mouseX, intradayViewportStart, intradayViewportSize, intradayPoints.size(), intradayChartCanvas.getWidth());
        double anchorRatio = resolveAnchorRatio(mouseX, intradayChartCanvas.getWidth());
        int nextStart = anchorIndex - (int) Math.round(anchorRatio * Math.max(0, nextVisible - 1));
        intradayViewportSize = nextVisible;
        intradayViewportStart = clampIntradayViewportStart(nextStart, intradayPoints.size(), intradayViewportSize);
        redrawIntradayChart();
    }

    private void panHistoricalViewport(double deltaX) {
        if (historicalViewportSize <= 0) {
            return;
        }
        int deltaSlots = toScrollSlots(0, deltaX);
        if (deltaSlots == 0) {
            return;
        }
        int nextStart = clampHistoricalViewportStart(
                historicalViewportStart + deltaSlots,
                historicalPoints.size(),
                historicalViewportSize);
        if (nextStart != historicalViewportStart) {
            historicalViewportStart = nextStart;
            redrawHistoricalChart();
        }
    }

    private void zoomHistoricalViewport(double mouseX, double deltaY) {
        if (Math.abs(deltaY) < 0.5) {
            return;
        }
        if (historicalViewportSize <= 0) {
            historicalViewportSize = resolveHistoricalVisibleCount(
                    historicalPoints.size(),
                    Math.max(1, historicalChartCanvas.getWidth() - CHART_LEFT_PAD - CHART_RIGHT_PAD));
        }
        int zoomSteps = Math.max(1, (int) Math.round(Math.abs(deltaY) / 40.0));
        int visibleChange = zoomSteps * 2;
        int direction = (int) Math.signum(deltaY);
        int nextVisible = direction > 0
                ? historicalViewportSize - visibleChange
                : historicalViewportSize + visibleChange;
        int minVisible = Math.min(CHART_MIN_ZOOM_VISIBLE_POINTS, historicalPoints.size());
        nextVisible = Math.max(minVisible, Math.min(nextVisible, historicalPoints.size()));
        if (nextVisible == historicalViewportSize) {
            return;
        }
        int anchorIndex = resolveAnchorIndex(mouseX, historicalViewportStart, historicalViewportSize, historicalPoints.size(), historicalChartCanvas.getWidth());
        double anchorRatio = resolveAnchorRatio(mouseX, historicalChartCanvas.getWidth());
        int nextStart = anchorIndex - (int) Math.round(anchorRatio * Math.max(0, nextVisible - 1));
        historicalViewportSize = nextVisible;
        historicalViewportStart = clampHistoricalViewportStart(nextStart, historicalPoints.size(), historicalViewportSize);
        redrawHistoricalChart();
    }

    private double resolveAnchorRatio(double mouseX, double canvasWidth) {
        double panelWidth = Math.max(1, canvasWidth - CHART_LEFT_PAD - CHART_RIGHT_PAD);
        double ratio = (mouseX - CHART_LEFT_PAD) / panelWidth;
        return Math.max(0, Math.min(1, ratio));
    }

    private int resolveAnchorIndex(double mouseX, int viewportStart, int viewportSize, int totalPoints, double canvasWidth) {
        if (totalPoints <= 0) {
            return 0;
        }
        double anchorRatio = resolveAnchorRatio(mouseX, canvasWidth);
        int offset = (int) Math.round(anchorRatio * Math.max(0, viewportSize - 1));
        int index = viewportStart + offset;
        return Math.max(0, Math.min(index, totalPoints - 1));
    }

    private void endIntradayPan() {
        if (!intradayPanActive) {
            return;
        }
        intradayPanActive = false;
        updateIntradayCursor();
    }

    private void updateIntradayCursor() {
        if (intradayPanActive) {
            intradayChartCanvas.setCursor(Cursor.CLOSED_HAND);
            return;
        }
        intradayChartCanvas.setCursor(isIntradayPannable() ? Cursor.OPEN_HAND : Cursor.DEFAULT);
    }

    private void resetSessionAvailability() {
        extendedHoursSupported = true;
        suppressSessionChangeHandler = true;
        sessionChoice.setValue(IntradaySession.REGULAR);
        suppressSessionChangeHandler = false;
        refreshSessionChoice();
    }

    private void updateSessionAvailability(IntradaySession snapshotSession, boolean providerExtendedHoursSupported) {
        if (snapshotSession != IntradaySession.EXTENDED) {
            return;
        }
        extendedHoursSupported = providerExtendedHoursSupported;
        if (!extendedHoursSupported && sessionChoice.getValue() == IntradaySession.EXTENDED) {
            sessionChoice.setValue(IntradaySession.REGULAR);
        }
        refreshSessionChoice();
    }

    private void refreshSessionChoice() {
        IntradaySession currentSelection = sessionChoice.getValue();
        sessionChoice.setItems(FXCollections.observableArrayList(IntradaySession.values()));
        sessionChoice.setValue(currentSelection == null ? IntradaySession.REGULAR : currentSelection);
    }

    private void showHistoricalEmptyMessage(String message) {
        historicalEmptyLabel.setText(message);
        historicalEmptyLabel.setVisible(true);
        historicalEmptyLabel.setManaged(true);
    }

    private void hideHistoricalEmptyMessage() {
        historicalEmptyLabel.setVisible(false);
        historicalEmptyLabel.setManaged(false);
    }

    private HistoricalInputValidation validateHistoricalInputs() {
        String symbol = normalizedHistoricalSymbol();
        LocalDate startDate = historicalStartDatePicker.getValue();
        LocalDate endDate = historicalEndDatePicker.getValue();
        StringJoiner message = new StringJoiner(" ");
        if (symbol.isEmpty()) {
            message.add("Enter a symbol.");
        }
        if (startDate == null) {
            message.add("Select a start date.");
        }
        if (endDate == null) {
            message.add("Select an end date.");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            message.add("Start date must be on or before end date.");
        }
        return new HistoricalInputValidation(symbol, startDate, endDate, message.toString());
    }

    private void updateHistoricalInputState() {
        HistoricalInputValidation validation = validateHistoricalInputs();
        boolean symbolPresent = !validation.symbol().isEmpty();
        historicalPreset30Button.setDisable(!symbolPresent);
        historicalPreset90Button.setDisable(!symbolPresent);
        historicalPreset120Button.setDisable(!symbolPresent);
        boolean canApply = validation.valid();
        historicalApplyRangeButton.setDisable(!canApply);
        historicalRetryButton.setDisable(!canApply);
        if (validation.valid()) {
            hideHistoricalValidationMessage();
            return;
        }
        showHistoricalValidationMessage(validation.message());
    }

    private void showHistoricalValidationMessage(String message) {
        if (message == null || message.isBlank()) {
            hideHistoricalValidationMessage();
            return;
        }
        historicalValidationLabel.setText(message);
        historicalValidationLabel.setVisible(true);
        historicalValidationLabel.setManaged(true);
    }

    private void hideHistoricalValidationMessage() {
        historicalValidationLabel.setVisible(false);
        historicalValidationLabel.setManaged(false);
    }

    private void showHistoricalRetry() {
        historicalRetryButton.setVisible(true);
        historicalRetryButton.setManaged(true);
    }

    private void hideHistoricalRetry() {
        historicalRetryButton.setVisible(false);
        historicalRetryButton.setManaged(false);
    }

    private void showHistoricalErrorBanner(String message) {
        historicalErrorBannerLabel.setText(message);
        historicalErrorBannerLabel.setVisible(true);
        historicalErrorBannerLabel.setManaged(true);
    }

    private void hideHistoricalErrorBanner() {
        historicalErrorBannerLabel.setVisible(false);
        historicalErrorBannerLabel.setManaged(false);
    }

    private enum IntradayViewState {
        IDLE,
        LOADING,
        HAS_DATA,
        MARKET_CLOSED,
        ERROR
    }

    private record WindowRange(int startInclusive, int endExclusive) {
    }

    private record HistoricalInputValidation(String symbol, LocalDate startDate, LocalDate endDate, String message) {
        private boolean valid() {
            return message.isBlank();
        }
    }
}
