/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static java.util.Collections.emptyList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import org.springframework.http.HttpHeaders;

class HeaderUtil {
  private static final MethodHandle GET_HEADERS;

  static {
    GET_HEADERS =
        isSpring7OrNewer()
            ? findGetHeadersMethod(MethodType.methodType(List.class, String.class))
            : findGetHeadersMethod(MethodType.methodType(List.class, Object.class));
  }

  private static boolean isSpring7OrNewer() {
    try {
      Class.forName("org.springframework.core.Nullness");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static MethodHandle findGetHeadersMethod(MethodType methodType) {
    try {
      return MethodHandles.lookup().findVirtual(HttpHeaders.class, "get", methodType);
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  @SuppressWarnings("unchecked") // casting MethodHandle.invoke result
  static List<String> getHeader(HttpHeaders headers, String name) {
    try {
      List<String> result = (List<String>) GET_HEADERS.invoke(headers, name);
      if (result == null) {
        return emptyList();
      }
      return result;
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private HeaderUtil() {}
}
