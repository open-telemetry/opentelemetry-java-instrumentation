package io.opentelemetry.auto.config;

import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.List;

public class AgentConfig {
  private static ConfigProvider defaultProvider =
      new StackedConfigProvider(
          new SystemPropertyConfigProvider(), new EnvironmentConfigProvider());

  public static ConfigProvider getDefault() {
    return defaultProvider;
  }

  @VisibleForTesting
  protected static void setDefault(final ConfigProvider provider) {
    defaultProvider = provider;
  }

  public static class EnvironmentConfigProvider implements ConfigProvider {
    @Override
    public String get(final String key) {
      return System.getenv(key.toUpperCase().replace(".", "_"));
    }
  }

  public static class StackedConfigProvider implements ConfigProvider {
    private final List<ConfigProvider> children;

    public StackedConfigProvider(final List<ConfigProvider> children) {
      this.children = children;
    }

    public StackedConfigProvider(final ConfigProvider... providers) {
      this(Arrays.asList(providers));
    }

    @Override
    public String get(final String key) {
      for (final ConfigProvider cp : children) {
        final String value = cp.get(key);
        if (value != null) {
          return value;
        }
      }
      return null;
    }
  }

  public static class SystemPropertyConfigProvider implements ConfigProvider {
    @Override
    public String get(final String key) {
      return System.getProperty(key);
    }
  }
}
