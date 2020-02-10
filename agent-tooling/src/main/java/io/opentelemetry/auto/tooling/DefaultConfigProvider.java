package io.opentelemetry.auto.tooling;

import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.exportersupport.ConfigProvider;

public class DefaultConfigProvider implements ConfigProvider {
  private final String prefix;

  public DefaultConfigProvider(final String prefix) {
    this.prefix = prefix;
  }

  // @Override
  @Override
  public String getString(final String key, final String defaultValue) {
    return Config.getSettingFromEnvironment(prefix + "." + key, defaultValue);
  }

  // @Override
  @Override
  public int getInt(final String key, final int defaultValue) {
    final String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Integer.parseInt(s); // TODO: Handle format errors gracefully?
  }

  // @Override
  @Override
  public long getLong(final String key, final int defaultValue) {
    final String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Long.parseLong(s); // TODO: Handle format errors gracefully?
  }

  // @Override
  @Override
  public boolean getBoolean(final String key, final boolean defaultValue) {
    final String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Boolean.parseBoolean(s); // TODO: Handle format errors gracefully?
  }

  // @Override
  @Override
  public double getDouble(final String key, final double defaultValue) {
    final String s = Config.getSettingFromEnvironment(prefix + "." + key, null);
    if (s == null) {
      return defaultValue;
    }
    return Double.parseDouble(s); // TODO: Handle format errors gracefully?
  }
}
