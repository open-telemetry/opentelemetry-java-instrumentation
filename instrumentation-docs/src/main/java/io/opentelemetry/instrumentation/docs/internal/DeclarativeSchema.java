/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Describes the per-item object schema of a {@code structured_list} declarative configuration. The
 * shape mirrors the JSON-schema style used by opentelemetry-configuration (and consumed by the
 * ecosystem explorer): an {@code object} with named {@code properties} and a list of {@code
 * required} keys.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public record DeclarativeSchema(
    String type, @Nullable List<String> required, Map<String, Property> properties) {

  /**
   * A single property within a {@link DeclarativeSchema}. This class is internal and is hence not
   * for public use. Its APIs are unstable and can change at any time.
   */
  public record Property(
      String type,
      @Nullable String description,
      @JsonProperty("default") @Nullable Object defaultValue) {}
}
