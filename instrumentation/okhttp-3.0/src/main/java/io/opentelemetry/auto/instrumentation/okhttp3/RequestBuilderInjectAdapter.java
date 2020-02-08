package io.opentelemetry.auto.instrumentation.okhttp3;

import io.opentelemetry.context.propagation.HttpTextFormat;
import okhttp3.Request;

/**
 * Helper class to inject span context into request headers.
 *
 * @author Pavol Loffay
 */
public class RequestBuilderInjectAdapter implements HttpTextFormat.Setter<Request.Builder> {

  public static final RequestBuilderInjectAdapter SETTER = new RequestBuilderInjectAdapter();

  @Override
  public void put(final Request.Builder carrier, final String key, final String value) {
    carrier.addHeader(key, value);
  }
}
