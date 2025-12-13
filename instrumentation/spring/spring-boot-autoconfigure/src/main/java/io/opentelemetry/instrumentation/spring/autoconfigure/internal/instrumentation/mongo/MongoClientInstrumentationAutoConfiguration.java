/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.mongo;

import com.mongodb.MongoClientSettings;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnClass({
  MongoClientSettings.class,
  MongoClientSettingsBuilderCustomizer.class
}) // module changed in Spring Boot 4
@ConditionalOnEnabledInstrumentation(module = "mongo")
@Configuration
public class MongoClientInstrumentationAutoConfiguration {

  @Bean
  MongoClientSettingsBuilderCustomizer customizer(OpenTelemetry openTelemetry) {
    return builder ->
        builder.addCommandListener(
            MongoTelemetry.builder(openTelemetry)
                .setStatementSanitizationEnabled(
                DeclarativeConfigUtil.getBoolean(
                    openTelemetry, "java", "mongo", "statement_sanitizer", "enabled")
                  .orElseGet(
                    () ->
                      DeclarativeConfigUtil.getBoolean(
                          openTelemetry,
                          "common",
                          "db_statement_sanitizer",
                          "enabled")
                        .orElse(true)))
                .build()
                .newCommandListener());
  }
}
