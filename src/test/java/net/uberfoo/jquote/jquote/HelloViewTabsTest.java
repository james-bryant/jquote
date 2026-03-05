package net.uberfoo.jquote.jquote;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelloViewTabsTest {

    @Test
    void tabsRenderInExpectedOrderWithOverviewDefault() throws Exception {
        Document document = loadFxml();
        NodeList tabNodes = document.getElementsByTagName("Tab");
        List<String> tabTitles = new ArrayList<>();
        for (int index = 0; index < tabNodes.getLength(); index++) {
            Element tab = (Element) tabNodes.item(index);
            tabTitles.add(tab.getAttribute("text"));
            assertFalse(Boolean.parseBoolean(tab.getAttribute("selected")),
                    "Tabs should rely on default first-tab selection.");
        }

        assertEquals(List.of("Overview", "Historical", "Intraday"), tabTitles);
        assertEquals("Overview", tabTitles.getFirst());
        assertTrue(hasFxId(document, "symbolInput"));
        assertTrue(hasFxId(document, "overviewHeadlineLabel"));
        assertTrue(hasFxId(document, "overviewSignalLabel"));
        assertTrue(hasFxId(document, "overviewIndicatorStatsLabel"));
    }

    @Test
    void historicalTabContainsRangeControls() throws Exception {
        Document document = loadFxml();

        assertTrue(hasFxId(document, "historicalStartDatePicker"));
        assertTrue(hasFxId(document, "historicalEndDatePicker"));
        assertTrue(hasFxId(document, "historicalStatusLabel"));
        assertTrue(hasFxId(document, "historicalValidationLabel"));
        assertTrue(hasFxId(document, "historicalErrorBannerLabel"));
        assertTrue(hasFxId(document, "historicalRetryButton"));
        assertTrue(hasFxId(document, "historicalApplyRangeButton"));
        assertTrue(hasFxId(document, "historicalPreset30Button"));
        assertTrue(hasFxId(document, "historicalPreset90Button"));
        assertTrue(hasFxId(document, "historicalPreset120Button"));
        assertTrue(hasFxId(document, "historicalChartCanvas"));
        assertTrue(hasFxId(document, "historicalTimestampLabel"));
        assertTrue(hasFxId(document, "historicalRsiToggle"));
        assertTrue(hasFxId(document, "historicalMacdToggle"));
        assertTrue(hasFxId(document, "historicalBollingerToggle"));
    }

    @Test
    void intradayTabContainsSessionToggleAndCandlestickCanvas() throws Exception {
        Document document = loadFxml();

        assertTrue(hasFxId(document, "symbolInput"));
        assertTrue(hasFxId(document, "sessionChoice"));
        assertTrue(hasFxId(document, "intradayChartCanvas"));
        assertTrue(hasFxId(document, "intradayEmptyLabel"));
        assertTrue(hasFxId(document, "intradayTimestampLabel"));
        assertTrue(hasFxId(document, "intradayRsiToggle"));
        assertTrue(hasFxId(document, "intradayMacdToggle"));
        assertTrue(hasFxId(document, "intradayBollingerToggle"));
        assertFalse(hasFxId(document, "quoteLabel"));
        assertFalse(hasFxId(document, "stateLabel"));
    }

    @Test
    void intradayTabDoesNotExposeMinuteResolutionControls() throws Exception {
        Document document = loadFxml();

        assertFalse(hasFxId(document, "intervalChoice"));
        assertFalse(hasText(document, "Interval"));
        assertFalse(hasText(document, "1m"));
        assertFalse(hasText(document, "15m"));
    }

    private static boolean hasFxId(Document document, String fxId) {
        NodeList nodes = document.getElementsByTagName("*");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (fxId.equals(element.getAttribute("fx:id"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(Document document, String text) {
        NodeList nodes = document.getElementsByTagName("*");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (text.equals(element.getAttribute("text"))) {
                return true;
            }
        }
        return false;
    }

    private static Document loadFxml() throws Exception {
        var resource = HelloViewTabsTest.class.getResourceAsStream("/net/uberfoo/jquote/jquote/hello-view.fxml");
        assertNotNull(resource, "hello-view.fxml should exist on classpath.");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(resource);
    }
}
