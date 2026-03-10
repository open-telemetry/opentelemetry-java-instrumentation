/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka.ProducerFactoryCustomizerConfiguration;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;

@DisabledInNativeImage // See GraalVmNativeKafkaSpringStarterSmokeTest for the GraalVM native test
class KafkaSpringStarterSmokeTest extends AbstractJvmKafkaSpringStarterSmokeTest {
  @Override
  protected Class<?> kafkaProducerFactoryCustomizerClass() {
    return ProducerFactoryCustomizerConfiguration.class;
  }

  @Override
  protected Class<?> kafkaAutoConfigurationClass() {
    return KafkaAutoConfiguration.class;
  }
}
