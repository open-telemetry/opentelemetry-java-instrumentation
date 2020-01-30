package io.opentelemetry.auto.test;

import io.opentelemetry.context.propagation.HttpTextFormat;
import java.util.Map;

public class MapGetter implements HttpTextFormat.Getter<Map<String, String>> {
  @Override
  public String get(final Map<String, String> o, final String s) {
    return o.get(s);
  }
}
