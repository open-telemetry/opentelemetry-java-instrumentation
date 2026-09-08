/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.core.io.support.SpringFactoriesLoader;

class DeclarativeConfigLoggingExporterAutoConfigurationTest {

  // SpanLoggingCustomizerProviderTest instantiates the provider directly, so it passes even when
  // Spring Boot never discovers this auto-configuration
  @Test
  void registeredAsAutoConfiguration() {
    assertThat(ImportCandidates.load(AutoConfiguration.class, null))
        .contains(DeclarativeConfigLoggingExporterAutoConfiguration.class.getName());
  }

  // Spring Boot before 2.7 does not read the AutoConfiguration.imports file
  @Test
  void registeredInSpringFactories() {
    assertThat(SpringFactoriesLoader.loadFactoryNames(EnableAutoConfiguration.class, null))
        .contains(DeclarativeConfigLoggingExporterAutoConfiguration.class.getName());
  }
}
