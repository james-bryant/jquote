package net.uberfoo.jquote.jquote;

import com.pangility.schwab.api.client.marketdata.EnableSchwabMarketDataApi;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableSchwabMarketDataApi
@EnableScheduling
@ConfigurationPropertiesScan
public class JQuoteBoot {
}
