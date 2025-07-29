/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import static java.util.Collections.emptySet;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import org.restlet.Message;
import org.restlet.Request;
import org.restlet.util.Series;

final class RestletHeadersGetter implements ExtendedTextMapGetter<Request> {

  private static final MethodHandle GET_ATTRIBUTES;

  static {
    MethodHandle getAttributes = null;

    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try {
      getAttributes =
          lookup.findVirtual(Message.class, "getAttributes", MethodType.methodType(Map.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // changed the return type to ConcurrentMap in version 2.1
      try {
        getAttributes =
            lookup.findVirtual(
                Message.class, "getAttributes", MethodType.methodType(ConcurrentMap.class));
      } catch (NoSuchMethodException | IllegalAccessException ex) {
        // ignored
      }
    }

    GET_ATTRIBUTES = getAttributes;
  }

  @Override
  public Iterable<String> keys(Request carrier) {
    Series<?> headers = getHeaders(carrier);
    return headers == null ? emptySet() : headers.getNames();
  }

  @Override
  public String get(Request carrier, String key) {
    Series<?> headers = getHeaders(carrier);
    return headers == null ? null : headers.getFirstValue(key, /* ignoreCase= */ true);
  }

  @Override
  public Iterator<String> getAll(Request carrier, String key) {
    Series<?> headers = getHeaders(carrier);
    return headers == null
        ? Collections.emptyIterator()
        : Arrays.asList(headers.getValuesArray(key, /* ignoreCase= */ true)).iterator();
  }

  @SuppressWarnings("unchecked")
  @Nullable
  static Series<?> getHeaders(Message carrier) {
    if (GET_ATTRIBUTES == null) {
      return null;
    }
    try {
      Map<String, Object> attributes = (Map<String, Object>) GET_ATTRIBUTES.invoke(carrier);
      return (Series<?>) attributes.get("org.restlet.http.headers");
    } catch (Throwable e) {
      return null;
    }
  }
}
