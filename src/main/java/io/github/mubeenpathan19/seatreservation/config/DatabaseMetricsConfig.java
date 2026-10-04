package io.github.mubeenpathan19.seatreservation.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
public class DatabaseMetricsConfig {
    @Bean
    public io.micrometer.core.instrument.Gauge databasePing(MeterRegistry registry, DataSource dataSource) {
        return io.micrometer.core.instrument.Gauge.builder("database_up", () -> {
            try (Connection c = dataSource.getConnection()) {
                return c.isValid(1) ? 1 : 0;
            } catch (Exception e) {
                return 0;
            }
        }).description("Database connectivity probe").register(registry);
    }
}
