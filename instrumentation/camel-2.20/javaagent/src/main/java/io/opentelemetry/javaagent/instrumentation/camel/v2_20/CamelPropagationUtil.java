/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static java.util.Collections.singletonMap;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.camel.Endpoint;

final class CamelPropagationUtil {

  private static final TextMapPropagator messagingPropagator = new CamelMessagingPropagator();

  private CamelPropagationUtil() {}

  static Context extractParent(Map<String, Object> exchangeHeaders, Endpoint endpoint) {
    return (isAwsPropagated(endpoint)
        ? extractAwsPropagationParent(exchangeHeaders)
        : extractHttpPropagationParent(exchangeHeaders));
  }

  private static boolean isAwsPropagated(Endpoint endpoint) {
    return endpoint.getClass().getName().endsWith("SqsEndpoint");
  }

  private static Context extractAwsPropagationParent(Map<String, Object> exchangeHeaders) {
    return AwsXrayPropagator.getInstance()
        .extract(
            Context.current(),
            singletonMap("X-Amzn-Trace-Id", exchangeHeaders.get("AWSTraceHeader")),
            MapGetter.INSTANCE);
  }

  private static Context extractHttpPropagationParent(Map<String, Object> exchangeHeaders) {
    return GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(Context.current(), exchangeHeaders, MapGetter.INSTANCE);
  }

  static void injectParent(Context context, Map<String, Object> exchangeHeaders) {
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(context, exchangeHeaders, MapSetter.INSTANCE);
  }

  static void clearPropagationFields(Map<String, Object> exchangeHeaders) {
    for (String field : GlobalOpenTelemetry.getPropagators().getTextMapPropagator().fields()) {
      exchangeHeaders.remove(field);
    }
  }

  static TextMapPropagator messagingPropagator() {
    return messagingPropagator;
  }

  static TextMapGetter<CamelRequest> messagingGetter() {
    return CamelRequestGetter.INSTANCE;
  }

  private static class CamelMessagingPropagator implements TextMapPropagator {

    @Override
    public Collection<String> fields() {
      Set<String> fields =
          new HashSet<>(GlobalOpenTelemetry.getPropagators().getTextMapPropagator().fields());
      fields.addAll(AwsXrayPropagator.getInstance().fields());
      return fields;
    }

    @Override
    public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
      GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, carrier, setter);
    }

    @Override
    public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
      if (carrier instanceof CamelRequest
          && isAwsPropagated(((CamelRequest) carrier).getEndpoint())) {
        return AwsXrayPropagator.getInstance().extract(context, carrier, getter);
      }
      return GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .extract(context, carrier, getter);
    }
  }

  private enum CamelRequestGetter implements TextMapGetter<CamelRequest> {
    INSTANCE;

    @Override
    public Iterable<String> keys(CamelRequest request) {
      return request.getExchange().getIn().getHeaders().keySet();
    }

    @Override
    @Nullable
    public String get(@Nullable CamelRequest request, String key) {
      if (request == null) {
        return null;
      }
      Map<String, Object> headers = request.getExchange().getIn().getHeaders();
      Object value =
          "X-Amzn-Trace-Id".equals(key) ? headers.get("AWSTraceHeader") : headers.get(key);
      return value == null ? null : value.toString();
    }
  }

  private enum MapGetter implements TextMapGetter<Map<String, Object>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, Object> map) {
      return map.keySet();
    }

    @Override
    @Nullable
    public String get(@Nullable Map<String, Object> map, String key) {
      if (map == null) {
        return null;
      }
      Object value = map.get(key);
      return (value == null ? null : value.toString());
    }
  }

  private enum MapSetter implements TextMapSetter<Map<String, Object>> {
    INSTANCE;

    @Override
    public void set(@Nullable Map<String, Object> carrier, String key, String value) {
      if (carrier == null) {
        return;
      }
      // Camel keys are internal ones
      if (!key.startsWith("Camel")) {
        carrier.put(key, value);
      }
    }
  }
}
