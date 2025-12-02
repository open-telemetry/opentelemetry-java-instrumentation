/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import org.springframework.boot.autoconfigure.AutoConfigurations;

class KafkaInstrumentationAutoConfigurationTest
    extends AbstractKafkaInstrumentationAutoConfigurationTest {

  @Override
  protected AutoConfigurations autoConfigurations() {
    return AutoConfigurations.of(KafkaInstrumentationAutoConfiguration.class);
  }
}
