/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka.KafkaInstrumentationSpringBoot4AutoConfiguration;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;

@DisabledInNativeImage // See GraalVmNativeKafkaSpringStarterSmokeTest for the GraalVM native test
class KafkaSpringStarterSmokeTest extends AbstractJvmKafkaSpringStarterSmokeTest {
  @Override
  protected Class<?> kafkaInstrumentationAutoConfigurationClass() {
    return KafkaInstrumentationSpringBoot4AutoConfiguration.class;
  }

  @Override
  protected Class<?> kafkaAutoConfigurationClass() {
    return KafkaAutoConfiguration.class;
  }
}
