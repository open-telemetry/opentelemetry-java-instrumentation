/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import io.opentelemetry.api.GlobalOpenTelemetry;

abstract class KafkaTracingHolder {

  static final KafkaTracing tracing = KafkaTracing.create(GlobalOpenTelemetry.get());
}
