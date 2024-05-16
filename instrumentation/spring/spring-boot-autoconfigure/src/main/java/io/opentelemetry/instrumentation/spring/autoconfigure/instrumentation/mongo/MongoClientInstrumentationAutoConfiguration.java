/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.mongo;

import com.mongodb.MongoClientSettings;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass(MongoClientSettings.class)
@ConditionalOnProperty(name = "otel.instrumentation.mongo.enabled", matchIfMissing = true)
@Conditional(SdkEnabled.class)
@Configuration
public class MongoClientInstrumentationAutoConfiguration {

  @Bean
  public MongoClientSettingsBuilderCustomizer customizer(
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
