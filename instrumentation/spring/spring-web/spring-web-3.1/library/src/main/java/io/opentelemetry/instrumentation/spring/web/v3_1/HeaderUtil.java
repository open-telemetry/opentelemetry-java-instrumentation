/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import static java.util.Collections.emptyList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.springframework.http.HttpHeaders;

class HeaderUtil {
  @Nullable private static final MethodHandle GET_HEADERS;

  static {
    GET_HEADERS =
        firstAvailableHandle(
            findGetHeadersMethod(
                MethodType.methodType(List.class, String.class)), // since spring web 7.0
            () ->
                findGetHeadersMethod(
                    MethodType.methodType(List.class, Object.class))); // before spring web 7.0
  }

  @Nullable
  private static MethodHandle firstAvailableHandle(
      @Nullable MethodHandle first, Supplier<? extends MethodHandle> supplier) {
    return first != null ? first : supplier.get();
  }

  @Nullable
  private static MethodHandle findGetHeadersMethod(MethodType methodType) {
    try {
      return MethodHandles.lookup().findVirtual(HttpHeaders.class, "get", methodType);
    } catch (Throwable t) {
      return null;
    }
  }

  @SuppressWarnings("unchecked") // casting GET_HEADERS.invoke result
  static List<String> getHeader(HttpHeaders headers, String name) {
    if (GET_HEADERS != null) {
      try {
        List<String> result = (List<String>) GET_HEADERS.invoke(headers, name);
        if (result != null) {
          return result;
        }
      } catch (Throwable t) {
        // ignore
      }
    }
    return emptyList();
  }

  private HeaderUtil() {}
}
