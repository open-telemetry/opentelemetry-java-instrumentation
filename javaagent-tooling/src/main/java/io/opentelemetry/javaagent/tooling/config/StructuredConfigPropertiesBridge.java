/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.StructuredConfigProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

// TODO: implement StructuredConfigProperties bridge to read flat properties
public final class StructuredConfigPropertiesBridge implements ConfigProperties {

  public StructuredConfigPropertiesBridge(StructuredConfigProperties structuredConfigProperties) {}

  @Nullable
  @Override
  public String getString(String s) {
    return null;
  }

  @Nullable
  @Override
  public Boolean getBoolean(String s) {
    return null;
  }

  @Nullable
  @Override
  public Integer getInt(String s) {
    return null;
  }

  @Nullable
  @Override
  public Long getLong(String s) {
    return null;
  }

  @Nullable
  @Override
  public Double getDouble(String s) {
    return null;
  }

  @Nullable
  @Override
  public Duration getDuration(String s) {
    return null;
  }

  @Override
  public List<String> getList(String s) {
    return Collections.emptyList();
  }

  @Override
  public Map<String, String> getMap(String s) {
    return Collections.emptyMap();
  }
}
