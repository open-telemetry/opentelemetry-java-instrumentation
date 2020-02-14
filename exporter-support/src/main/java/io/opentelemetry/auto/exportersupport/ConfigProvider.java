package io.opentelemetry.auto.exportersupport;

public interface ConfigProvider {
  String getString(String key, String defaultValue);

  int getInt(String key, int defaultValue);

  long getLong(String key, long defaultValue);

  boolean getBoolean(String key, boolean defaultValue);

  double getDouble(String key, double defaultValue);
}
