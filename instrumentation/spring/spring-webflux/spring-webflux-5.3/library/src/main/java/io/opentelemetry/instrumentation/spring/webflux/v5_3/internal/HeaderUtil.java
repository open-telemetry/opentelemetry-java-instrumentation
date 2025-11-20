/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static java.util.Collections.emptyList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class HeaderUtil {
  private static final MethodHandle GET_HEADERS;

  static {
    // since spring web 7.0
    GET_HEADERS = findGetHeadersMethod(MethodType.methodType(List.class, String.class, List.class));
  }

  private static MethodHandle findGetHeadersMethod(MethodType methodType) {
    try {
      return MethodHandles.lookup().findVirtual(HttpHeaders.class, "getOrDefault", methodType);
    } catch (Throwable t) {
      return null;
    }
  }

  // before spring web 7.0 HttpHeaders implements Map<String, List<String>>, this triggers
  // errorprone BadInstanceof warning since errorpone is not aware that this instanceof check does
  // not pass with spring web 7.0+
  @SuppressWarnings({"unchecked", "BadInstanceof"})
  public static List<String> getHeader(HttpHeaders headers, String name) {
    if (headers instanceof Map) {
      // before spring web 7.0
      return ((Map<String, List<String>>) headers).getOrDefault(name, emptyList());
    } else if (GET_HEADERS != null) {
      // spring web 7.0
      try {
        return (List<String>) GET_HEADERS.invoke(headers, name, emptyList());
      } catch (Throwable t) {
        // ignore
      }
    }
    return emptyList();
  }

  private HeaderUtil() {}
}
