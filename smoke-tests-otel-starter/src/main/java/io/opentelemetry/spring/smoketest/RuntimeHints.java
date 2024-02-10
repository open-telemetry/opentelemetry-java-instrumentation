/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.data.web.config.SpringDataJacksonConfiguration;

// Necessary for GraalVM native test
public class RuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(
      org.springframework.aot.hint.RuntimeHints hints, ClassLoader classLoader) {
    hints.resources().registerResourceBundle("org.apache.commons.dbcp2.LocalStrings");

    // To remove from Spring Boot 3.2.3 release:
    // https://github.com/spring-projects/spring-data-commons/issues/3025
    hints
        .reflection()
        .registerType(
            TypeReference.of("org.springframework.data.domain.Unpaged"),
            hint -> {
              hint.onReachableType(SpringDataJacksonConfiguration.PageModule.class);
            });
  }
}
