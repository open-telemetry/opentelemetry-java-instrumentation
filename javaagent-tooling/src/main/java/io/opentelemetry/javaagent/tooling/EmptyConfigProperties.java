/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public enum EmptyConfigProperties implements ConfigProperties {
  INSTANCE;

  @Nullable
  @Override
  public String getString(String name) {
    return null;
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return null;
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return null;
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return null;
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return null;
  }

  @Nullable
  @Override
  public Duration getDuration(String name) {
    return null;
  }

  @Override
  public List<String> getList(String name) {
    return emptyList();
  }

  @Override
  public Map<String, String> getMap(String name) {
    return emptyMap();
  }
}
