/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.util.function.BooleanSupplier;

// Classes used by multiple instrumentations should be in a bootstrap module to ensure that all
// instrumentations see the same class. Helper classes are injected into each class loader that
// contains an instrumentation that uses them, so instrumentations in different class loaders will
// have separate copies of helper classes.
public final class KafkaClientsConsumerProcessTracing {
  private static final Cache<Object, Boolean> consumedMessageCounted = Cache.weak();
  private static final ThreadLocal<Boolean> wrappingEnabled = ThreadLocal.withInitial(() -> true);

  public static boolean setWrappingEnabled(boolean enabled) {
    boolean previous = wrappingEnabled.get();
    wrappingEnabled.set(enabled);
    return previous;
  }

  public static boolean isWrappingEnabled() {
    return wrappingEnabled.get();
  }

  public static BooleanSupplier getWrappingEnabledSupplier() {
    return KafkaClientsConsumerProcessTracing::isWrappingEnabled;
  }

  public static void markConsumedMessageCounted(Object message) {
    consumedMessageCounted.put(message, Boolean.TRUE);
  }

  public static void copyConsumedMessageCounted(Object source, Object target) {
    if (source != null && wasConsumedMessageCounted(source)) {
      markConsumedMessageCounted(target);
    } else {
      consumedMessageCounted.remove(target);
    }
  }

  public static boolean wasConsumedMessageCounted(Object message) {
    return Boolean.TRUE.equals(consumedMessageCounted.get(message));
  }

  private KafkaClientsConsumerProcessTracing() {}
}
