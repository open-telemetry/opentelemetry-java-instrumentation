/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.kafka.clients.consumer;

import java.util.List;
import org.apache.kafka.common.metrics.MetricsReporter;

public class KafkaConsumerAccess {

  private KafkaConsumerAccess() {}

  public static List<MetricsReporter> getMetricsReporters(KafkaConsumer<?, ?> consumer) {
    return consumer.metrics.reporters();
  }
}
