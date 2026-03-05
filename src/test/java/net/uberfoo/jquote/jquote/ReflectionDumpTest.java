package net.uberfoo.jquote.jquote;

import com.pangility.schwab.api.client.marketdata.SchwabMarketDataApiClient;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.PriceHistoryRequest;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.PriceHistoryResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

class ReflectionDumpTest {
    @Test
    void dumpMethods() {
        dump("SchwabMarketDataApiClient", SchwabMarketDataApiClient.class);
        dump("PriceHistoryRequest", PriceHistoryRequest.class);
        dump("PriceHistoryResponse", PriceHistoryResponse.class);
    }

    private static void dump(String label, Class<?> type) {
        System.out.println("=== " + label + " ===");
        Arrays.stream(type.getMethods())
                .map(Method::toString)
                .sorted()
                .forEach(System.out::println);
    }
}
