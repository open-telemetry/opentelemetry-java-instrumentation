/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Map;

/**
 * A {@link ConfigProperties} which resolves properties based on {@link
 * DeclarativeConfigProperties}.
 *
 * <p>Only properties starting with "otel.instrumentation." are resolved. Others return null (or
 * default value if provided).
 *
 * <p>To resolve:
 *
 * <ul>
 *   <li>"otel.instrumentation" refers to the ".instrumentation.java" node
 *   <li>The portion of the property after "otel.instrumentation." is split into segments based on
 *       ".".
 *   <li>For each N-1 segment, we walk down the tree to find the relevant leaf {@link
 *       DeclarativeConfigProperties}.
 *   <li>We extract the property from the resolved {@link DeclarativeConfigProperties} using the
 *       last segment as the property key.
 * </ul>
 *
 * <p>For example, given the following YAML, asking for {@code
 * ConfigProperties#getString("otel.instrumentation.common.string_key")} yields "value":
 *
 * <pre>
 *   instrumentation:
 *     java:
 *       common:
 *         string_key: value
 * </pre>
 */
final class DeclarativeConfigPropertiesBridge extends DeclarativeConfigPropertiesApiBridge
    implements ConfigProperties {

  DeclarativeConfigPropertiesBridge(
      DeclarativeConfigProperties baseNode,
      Map<String, String> mappings,
      Map<String, Object> overrideValues) {
    super(baseNode, mappings, overrideValues);
  }
}
