/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.mongo;

import com.mongodb.MongoClientSettings;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.InstrumentationConfigUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnClass({MongoClientSettings.class, MongoClientSettingsBuilderCustomizer.class})
@ConditionalOnEnabledInstrumentation(module = "mongo")
@ConditionalOnMissingClass(
    "org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer") // Spring
// Boot 2 &
// 3
@Configuration
public class MongoClientInstrumentationSpringBoot4AutoConfiguration {

  @Bean
  MongoClientSettingsBuilderCustomizer customizer(
      OpenTelemetry openTelemetry, InstrumentationConfig config) {
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
