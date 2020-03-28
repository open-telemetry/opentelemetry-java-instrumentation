/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.tooling;

import io.opentelemetry.auto.config.Config;

public class DefaultExporterConfig implements io.opentelemetry.sdk.contrib.auto.config.Config {
  private final String prefix;

  public DefaultExporterConfig(final String prefix) {
    this.prefix = prefix;
  }

  @Override
  public String getString(final String key, final String defaultValue) {
    return Config.getSettingFromEnvironment(prefix + "." + key, defaultValue);
  }

  @Override
  public int getInt(final String key, final int defaultValue) {
    final String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Integer.parseInt(s); // TODO: Handle format errors gracefully?
  }

  @Override
  public long getLong(final String key, final long defaultValue) {
    final String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Long.parseLong(s); // TODO: Handle format errors gracefully?
  }

  @Override
  public boolean getBoolean(final String key, final boolean defaultValue) {
    final String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Boolean.parseBoolean(s); // TODO: Handle format errors gracefully?
  }

  @Override
  public double getDouble(final String key, final double defaultValue) {
    final String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Double.parseDouble(s); // TODO: Handle format errors gracefully?
  }
}
