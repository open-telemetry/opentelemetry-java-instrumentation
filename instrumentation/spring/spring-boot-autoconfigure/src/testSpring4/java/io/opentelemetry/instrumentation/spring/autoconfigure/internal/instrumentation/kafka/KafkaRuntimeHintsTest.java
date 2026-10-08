/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;

class KafkaRuntimeHintsTest {

  @ParameterizedTest
  @ValueSource(strings = {"batchInterceptor", "recordInterceptor"})
  void registersInterceptorFields(String fieldName) {
    RuntimeHints hints = new RuntimeHints();

    assertThat(
            SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
                .load(RuntimeHintsRegistrar.class))
        .filteredOn(registrar -> registrar instanceof KafkaRuntimeHints)
        .singleElement()
        .satisfies(registrar -> registrar.registerHints(hints, getClass().getClassLoader()));

    assertThat(
            RuntimeHintsPredicates.reflection()
                .onFieldAccess(AbstractKafkaListenerContainerFactory.class, fieldName))
        .accepts(hints);
  }

  @Test
  void skipsHintsWhenKafkaIsAbsent() throws IOException {
    RuntimeHints hints = new RuntimeHints();

    try (FilteredClassLoader classLoader = new FilteredClassLoader("org.springframework.kafka")) {
      new KafkaRuntimeHints().registerHints(hints, classLoader);
    }

    assertThat(
            hints
                .reflection()
                .getTypeHint(
                    TypeReference.of(
                        "org.springframework.kafka.config.AbstractKafkaListenerContainerFactory")))
        .isNull();
  }
}
