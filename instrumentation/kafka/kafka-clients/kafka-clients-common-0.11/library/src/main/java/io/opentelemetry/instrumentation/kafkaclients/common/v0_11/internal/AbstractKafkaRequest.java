/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import java.util.List;
import javax.annotation.Nullable;

abstract class AbstractKafkaRequest {

  @Nullable private final List<String> bootstrapServers;

  protected AbstractKafkaRequest(@Nullable List<String> bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  @Nullable
  public List<String> getBootstrapServers() {
    return bootstrapServers;
  }
}
