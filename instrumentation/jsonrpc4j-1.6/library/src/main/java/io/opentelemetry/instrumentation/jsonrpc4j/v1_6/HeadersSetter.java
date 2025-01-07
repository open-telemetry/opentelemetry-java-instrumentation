package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Map;

public enum HeadersSetter implements TextMapSetter<Map<String, String>> {
  INSTANCE;

  @Override
  public void set(Map<String, String> carrier, String key, String value) {
    assert carrier != null;
    carrier.put(key, value);
  }
}
