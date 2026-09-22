/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package redis.clients.jedis;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

// This helper is in the Jedis package in order to use the package-private JedisFactory as the
// virtual field carrier.
public final class JedisFactoryTarget {

  private static final VirtualField<JedisFactory, JedisFactoryTarget> TARGET =
      VirtualField.find(JedisFactory.class, JedisFactoryTarget.class);

  private final Object value;

  private JedisFactoryTarget(Object value) {
    this.value = value;
  }

  public static boolean isInstance(Object factory) {
    return factory instanceof JedisFactory;
  }

  public static void set(Object factory, Object target) {
    TARGET.set((JedisFactory) factory, new JedisFactoryTarget(target));
  }

  @Nullable
  public static Object get(Object factory) {
    if (!(factory instanceof JedisFactory)) {
      return null;
    }
    JedisFactoryTarget target = TARGET.get((JedisFactory) factory);
    return target == null ? null : target.value;
  }
}
