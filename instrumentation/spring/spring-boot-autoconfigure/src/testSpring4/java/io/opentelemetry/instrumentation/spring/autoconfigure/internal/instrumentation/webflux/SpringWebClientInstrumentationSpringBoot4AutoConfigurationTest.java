/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractWebClientCustomizerAutoConfigurationTest;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.web.reactive.function.client.WebClient;

class SpringWebClientInstrumentationSpringBoot4AutoConfigurationTest
    extends AbstractWebClientCustomizerAutoConfigurationTest {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(SpringWebClientInstrumentationSpringBoot4AutoConfiguration.class);
  }

  @Override
  protected Class<?> webClientCustomizerClass() {
    return WebClientCustomizer.class;
  }

  @Override
  protected void customizeWebClient(Object customizer, WebClient.Builder builder) {
    ((WebClientCustomizer) customizer).customize(builder);
  }
}
