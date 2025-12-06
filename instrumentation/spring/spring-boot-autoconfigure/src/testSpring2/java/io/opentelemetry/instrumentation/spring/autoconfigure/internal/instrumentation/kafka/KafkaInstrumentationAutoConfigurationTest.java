/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractKafkaInstrumentationAutoConfigurationTest;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class KafkaInstrumentationAutoConfigurationTest
    extends AbstractKafkaInstrumentationAutoConfigurationTest {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(
        KafkaInstrumentationAutoConfiguration.class, ProducerFactoryCustomizerConfiguration.class);
  }

  @Override
  protected void factoryTestAssertion(AssertableApplicationContext context) {
    DefaultKafkaProducerFactoryCustomizer customizer =
        context.getBean(
            "otelKafkaProducerFactoryCustomizer", DefaultKafkaProducerFactoryCustomizer.class);
    assertThat(customizer).isNotNull();

    // Verify the customizer works by applying it to a producer factory
    DefaultKafkaProducerFactory<Object, Object> factory =
        new DefaultKafkaProducerFactory<>(emptyMap());
    customizer.customize(factory);

    // Check that interceptors were added (the customizer adds a post processor)
    assertThat(factory.getPostProcessors()).isNotEmpty();
  }
}
