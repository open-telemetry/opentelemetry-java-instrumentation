/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.mongo;

import com.mongodb.MongoClientSettings;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass(MongoClientSettings.class)
@ConditionalOnEnabledInstrumentation(module = "mongo")
@Configuration
public class MongoClientInstrumentationAutoConfiguration {

  @Bean
  MongoClientSettingsBuilderCustomizer customizer(
      OpenTelemetry openTelemetry, ConfigProperties config) {
    return builder ->
        builder.addCommandListener(
            MongoTelemetry.builder(openTelemetry)
                .setStatementSanitizationEnabled(
                    InstrumentationConfigUtil.isStatementSanitizationEnabled(
                        config, "otel.instrumentation.mongo.statement-sanitizer.enabled"))
                .build()
                .newCommandListener());
  }
}
