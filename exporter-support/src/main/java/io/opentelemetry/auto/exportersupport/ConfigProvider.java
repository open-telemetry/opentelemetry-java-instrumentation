package io.opentelemetry.auto.exportersupport;

public interface ConfigProvider {
  String getString(String key, String defaultValue);

  int getInt(String key, int defaultValue);

  long getLong(String key, int defaultValue);

  boolean getBoolean(String key, boolean defaultValue);

  double getDouble(String key, double defaultValue);
}
