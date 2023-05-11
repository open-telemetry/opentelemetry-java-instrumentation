/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.kafka.clients.producer;

import java.util.List;
import org.apache.kafka.common.metrics.MetricsReporter;

public class KafkaProducerAccess {

  private KafkaProducerAccess() {}

  public static List<MetricsReporter> getMetricsReporters(KafkaProducer<?, ?> producer) {
    return producer.metrics.reporters();
  }
}
