/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.springframework.core.env.Environment;

/**
 * A caching wrapper around Spring's {@link Environment} that caches property lookups to avoid
 * repeated expensive operations and property source traversal.
 *
 * <p>Thread-safe for concurrent access. Cached values persist indefinitely and are assumed to be
 * immutable after the first access. If properties can change at runtime, use {@link #clear()} to
 * invalidate the cache.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
final class CachedPropertyResolver {
  private final Environment environment;
  private final ConcurrentHashMap<CacheKey, Optional<?>> cache = new ConcurrentHashMap<>();

  CachedPropertyResolver(Environment environment) {
    this.environment = Objects.requireNonNull(environment, "environment");
  }

  /**
   * Gets a property value from the environment, using a cache to avoid repeated lookups.
   *
   * @param name the property name
   * @param targetType the target type to convert to
   * @return the property value, or null if not found
   */
  @Nullable
  <T> T getProperty(String name, Class<T> targetType) {
    CacheKey key = new CacheKey(name, targetType);
    // CacheKey includes targetType, ensuring type match
    @SuppressWarnings("unchecked")
    Optional<T> result =
        (Optional<T>)
            cache.computeIfAbsent(
                key, k -> Optional.ofNullable(environment.getProperty(name, targetType)));
    return result.orElse(null);
  }

  /**
   * Gets a string property value from the environment.
   *
   * @param name the property name
   * @return the property value, or null if not found
   */
  @Nullable
  String getProperty(String name) {
    return getProperty(name, String.class);
  }

  /** Clears all cached property values, forcing fresh lookups on subsequent calls. */
  void clear() {
    cache.clear();
  }

  /** Cache key combining property name and target type. */
  private static final class CacheKey {
    private final String name;
    private final Class<?> targetType;
    private final int cachedHashCode;

    CacheKey(String name, Class<?> targetType) {
      this.name = Objects.requireNonNull(name, "name");
      this.targetType = Objects.requireNonNull(targetType, "targetType");
      this.cachedHashCode = Objects.hash(name, targetType);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof CacheKey)) {
        return false;
      }
      CacheKey other = (CacheKey) obj;
      return name.equals(other.name) && targetType.equals(other.targetType);
    }

    @Override
    public int hashCode() {
      return cachedHashCode;
    }
  }
}
