/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Scope;

@AutoValue
abstract class ContextAndScope {

  static ContextAndScope create(
      RocketMqConsumerInstrumenter.ConsumerContext consumerContext, Scope scope) {
    return new AutoValue_ContextAndScope(consumerContext, scope);
  }

  abstract RocketMqConsumerInstrumenter.ConsumerContext getConsumerContext();

  abstract Scope getScope();

  void close() {
    getScope().close();
  }
}
