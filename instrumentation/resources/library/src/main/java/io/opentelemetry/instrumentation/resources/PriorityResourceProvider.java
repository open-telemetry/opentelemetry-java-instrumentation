/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class PriorityResourceProvider<D> implements ConditionalResourceProvider {

  // API

  protected abstract int priority();

  protected abstract Optional<D> readData();

  protected abstract void registerAttributes(AttributeBuilder attributeBuilder);

  protected String cacheKey() {
    return getClass().getName();
  }

  // visible for tests
  public static void resetForTest() {
    cache.clear();
  }

  // implementation

  public class AttributeBuilder {

    private AttributeBuilder() {}

    @CanIgnoreReturnValue
    public <T> AttributeBuilder add(AttributeKey<T> key, Function<D, Optional<T>> getter) {
      attributeGetters.put((AttributeKey) key, Objects.requireNonNull((Function) getter));
      return this;
    }
  }

  private static final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

  private static final ThreadLocal<Resource> existingResource = new ThreadLocal<>();

  private final Map<AttributeKey<Object>, Function<D, Optional<?>>> attributeGetters =
      new HashMap<>();

  public PriorityResourceProvider() {
    registerAttributes(new AttributeBuilder());
  }

  @Override
  public final int order() {
    return -priority();
  }

  @Override
  public final boolean shouldApply(ConfigProperties config, Resource existing) {
    existingResource.set(existing);

    Map<String, String> resourceAttributes = getResourceAttributes(config);
    return attributeGetters.keySet().stream()
        .allMatch(key -> shouldUpdate(config, existing, key, resourceAttributes));
  }

  @Override
  public final Resource createResource(ConfigProperties config) {
    return getData()
        .map(
            data -> {
              // what should we do here?
              // we don't have access to the existing resource
              // if the resource provider produces a single key, we can rely on shouldApply
              // i.e. this method won't be called if the key is already present
              // the thread local is a hack to work around this
              Resource existing =
                  Objects.requireNonNull(existingResource.get(), "call shouldApply first");
              Map<String, String> resourceAttributes = getResourceAttributes(config);
              AttributesBuilder builder = Attributes.builder();
              attributeGetters.entrySet().stream()
                  .filter(e -> shouldUpdate(config, existing, e.getKey(), resourceAttributes))
                  .forEach(
                      e ->
                          e.getValue()
                              .apply(data)
                              .ifPresent(value -> putAttribute(builder, e.getKey(), value)));
              return Resource.create(builder.build());
            })
        .orElse(Resource.empty());
  }

  private Optional<D> getData() {
    return (Optional<D>) cache.computeIfAbsent(cacheKey(), k -> readData());
  }

  private static <T> void putAttribute(AttributesBuilder builder, AttributeKey<T> key, T value) {
    builder.put(key, value);
  }

  private static Map<String, String> getResourceAttributes(ConfigProperties config) {
    return config.getMap("otel.resource.attributes");
  }

  private static boolean shouldUpdate(
      ConfigProperties config,
      Resource existing,
      AttributeKey<?> key,
      Map<String, String> resourceAttributes) {
    if (resourceAttributes.containsKey(key.getKey())) {
      return false;
    }

    Object value = existing.getAttribute(key);

    if (key.equals(ResourceAttributes.SERVICE_NAME)) {
      return config.getString("otel.service.name") == null && "unknown_service:java".equals(value);
    }

    return value == null;
  }
}
