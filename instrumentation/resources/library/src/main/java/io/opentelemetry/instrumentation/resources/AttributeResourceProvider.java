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
import io.opentelemetry.semconv.ServiceAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An easier alternative to {@link io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider}, which
 * avoids some common pitfalls and boilerplate.
 *
 * <p>An example of how to use this interface can be found in {@link ManifestResourceProvider}.
 */
public abstract class AttributeResourceProvider<D> implements ConditionalResourceProvider {

  private final AttributeProvider<D> attributeProvider;

  public class AttributeBuilder implements AttributeProvider.Builder<D> {

    private AttributeBuilder() {}

    @CanIgnoreReturnValue
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> AttributeBuilder add(AttributeKey<T> key, Function<D, Optional<T>> getter) {
      attributeGetters.put((AttributeKey) key, Objects.requireNonNull((Function) getter));
      return this;
    }
  }

  private Set<AttributeKey<?>> filteredKeys;

  private final Map<AttributeKey<Object>, Function<D, Optional<?>>> attributeGetters =
      new HashMap<>();

  AttributeResourceProvider(AttributeProvider<D> attributeProvider) {
    this.attributeProvider = attributeProvider;
    attributeProvider.registerAttributes(new AttributeBuilder());
  }

  @Override
  public final boolean shouldApply(ConfigProperties config, Resource existing) {
    Map<String, String> resourceAttributes = config.getMap("otel.resource.attributes");
    filteredKeys =
        attributeGetters.keySet().stream()
            .filter(key -> shouldUpdate(config, existing, key, resourceAttributes))
            .collect(Collectors.toSet());
    return !filteredKeys.isEmpty();
  }

  @Override
  public final Resource createResource(ConfigProperties config) {
    return attributeProvider
        .readData()
        .map(
            data -> {
              if (filteredKeys == null) {
                throw new IllegalStateException("shouldApply should be called first");
              }
              AttributesBuilder builder = Attributes.builder();
              attributeGetters.entrySet().stream()
                  .filter(e -> filteredKeys.contains(e.getKey()))
                  .forEach(
                      e ->
                          e.getValue()
                              .apply(data)
                              .ifPresent(value -> putAttribute(builder, e.getKey(), value)));
              return Resource.create(builder.build());
            })
        .orElse(Resource.empty());
  }

  private static <T> void putAttribute(AttributesBuilder builder, AttributeKey<T> key, T value) {
    builder.put(key, value);
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

    if (key.equals(ServiceAttributes.SERVICE_NAME)) {
      return config.getString("otel.service.name") == null && "unknown_service:java".equals(value);
    }

    return value == null;
  }
}
