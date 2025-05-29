/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

import java.util.function.BooleanSupplier;

// Classes used by multiple instrumentations should be in a bootstrap module to ensure that all
// instrumentations see the same class. Helper classes are injected into each class loader that
// contains an instrumentation that uses them, so instrumentations in different class loaders will
// have separate copies of helper classes.
public final class KafkaClientsConsumerProcessTracing {
  private static final ThreadLocal<Boolean> wrappingEnabled = ThreadLocal.withInitial(() -> true);

  private KafkaClientsConsumerProcessTracing() {}

  public static boolean setEnabled(boolean enabled) {
    boolean previous = wrappingEnabled.get();
    wrappingEnabled.set(enabled);
    return previous;
  }

  public static boolean wrappingEnabled() {
    return wrappingEnabled.get();
  }

  public static BooleanSupplier wrappingEnabledSupplier() {
    return KafkaClientsConsumerProcessTracing::wrappingEnabled;
  }
}
