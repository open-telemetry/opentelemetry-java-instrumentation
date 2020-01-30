package io.opentelemetry.auto.test;

import io.opentelemetry.context.propagation.HttpTextFormat;
import java.util.Map;

public class MapSetter implements HttpTextFormat.Setter<Map<String, String>> {
  @Override
  public void put(final Map<String, String> map, final String k, final String v) {
    map.put(k, v);
  }
}
