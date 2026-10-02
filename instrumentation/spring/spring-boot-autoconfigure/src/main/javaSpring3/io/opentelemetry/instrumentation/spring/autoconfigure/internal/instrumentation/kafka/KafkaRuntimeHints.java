/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.ClassUtils;

class KafkaRuntimeHints implements RuntimeHintsRegistrar {

  private static final String CONTAINER_FACTORY =
      "org.springframework.kafka.config.AbstractKafkaListenerContainerFactory";

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    if (ClassUtils.isPresent(CONTAINER_FACTORY, classLoader)) {
      hints
          .reflection()
          .registerType(
              TypeReference.of(CONTAINER_FACTORY),
              hint -> hint.withField("batchInterceptor").withField("recordInterceptor"));
    }
  }
}
