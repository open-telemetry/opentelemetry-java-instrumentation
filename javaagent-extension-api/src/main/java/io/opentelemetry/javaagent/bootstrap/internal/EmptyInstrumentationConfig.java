/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class EmptyInstrumentationConfig implements InstrumentationConfig {

  @Nullable
  @Override
  public String getString(String name) {
    return null;
  }

  @Override
  public String getString(String name, String defaultValue) {
    return defaultValue;
  }

  @Override
  public boolean getBoolean(String name, boolean defaultValue) {
    return defaultValue;
  }

  @Override
  public int getInt(String name, int defaultValue) {
    return defaultValue;
  }

  @Override
  public long getLong(String name, long defaultValue) {
    return defaultValue;
  }

  @Override
  public double getDouble(String name, double defaultValue) {
    return defaultValue;
  }

  @Override
  public Duration getDuration(String name, Duration defaultValue) {
    return defaultValue;
  }

  @Override
  public List<String> getList(String name, List<String> defaultValue) {
    return defaultValue;
  }

  @Override
  public Map<String, String> getMap(String name, Map<String, String> defaultValue) {
    return defaultValue;
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getDeclarativeConfig(String instrumentationName) {
    return null;
  }
}
