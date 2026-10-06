/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadSuppression;
import java.util.function.BooleanSupplier;

// Classes used by multiple instrumentations should be in a bootstrap module to ensure that all
// instrumentations see the same class. Helper classes are injected into each class loader that
// contains an instrumentation that uses them, so instrumentations in different class loaders will
// have separate copies of helper classes.
public final class KafkaClientsConsumerProcessTracing {

  private static final ScopedThreadSuppression processSpanSuppression =
      new ScopedThreadSuppression();

  public static ScopedThreadSuppression processSpanSuppression() {
    return processSpanSuppression;
  }

  public static BooleanSupplier processSpanEnabledSupplier() {
    return () -> !processSpanSuppression.isActive();
  }

  private KafkaClientsConsumerProcessTracing() {}
}
