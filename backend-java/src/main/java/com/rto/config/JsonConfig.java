package com.rto.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/** JSON contract: snake_case names, ISO dates, money as exact strings ("1500.50") - never floating point. */
@Configuration
public class JsonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer rtoJson() {
        return b -> {
            SimpleModule money = new SimpleModule("money");
            money.addSerializer(BigDecimal.class, new StdSerializer<>(BigDecimal.class) {
                @Override
                public void serialize(BigDecimal v, JsonGenerator g, SerializerProvider p) throws IOException {
                    g.writeString(v.toPlainString());
                }
            });
            b.modulesToInstall(money);
            b.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            b.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }
}
