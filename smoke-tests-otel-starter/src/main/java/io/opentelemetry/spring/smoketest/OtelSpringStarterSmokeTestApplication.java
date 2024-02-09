/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.resources.Resource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(RuntimeHints.class)
public class OtelSpringStarterSmokeTestApplication {

  public OtelSpringStarterSmokeTestApplication() {}

  public static void main(String[] args) {
    SpringApplication.run(OtelSpringStarterSmokeTestApplication.class);
  }

  @Bean
  AutoConfigurationCustomizerProvider propagatorCustomizer() {
    return customizer ->
        customizer.addResourceCustomizer(
            (resource, config) ->
                resource.merge(
                    Resource.create(
                        Attributes.of(
                            AttributeKey.booleanKey("AutoConfigurationCustomizerProvider"),
                            true))));
  }
}
