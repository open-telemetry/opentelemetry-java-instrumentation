package io.opentelemetry.auto.config;

public interface ConfigProvider {
  String get(String key);
}
