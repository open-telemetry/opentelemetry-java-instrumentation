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
    extends AbstractWebClientCustomizerAutoConfigurationTest<WebClientCustomizer> {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(SpringWebClientInstrumentationSpringBoot4AutoConfiguration.class);
  }

  @Override
  protected Class<WebClientCustomizer> webClientCustomizerClass() {
    return WebClientCustomizer.class;
  }

  @Override
  protected void customizeWebClient(WebClientCustomizer customizer, WebClient.Builder builder) {
    customizer.customize(builder);
  }
}
