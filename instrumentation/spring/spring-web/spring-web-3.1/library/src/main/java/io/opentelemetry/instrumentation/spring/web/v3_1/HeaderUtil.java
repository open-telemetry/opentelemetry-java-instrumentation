/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.springframework.http.HttpHeaders;

class HeaderUtil {
  private static final MethodHandle GET_HEADERS;

  static {
    GET_HEADERS =
        requireNonNullElseGet(
            findGetHeadersMethod(
                MethodType.methodType(List.class, String.class)), // since spring web 7.0
            () ->
                findGetHeadersMethod(
                    MethodType.methodType(List.class, Object.class))); // before spring web 7.0
  }

  // copied from java.util.Objects in Java 9+
  private static <T> T requireNonNullElseGet(@Nullable T obj, Supplier<? extends T> supplier) {
    return (obj != null)
        ? obj
        : requireNonNull(requireNonNull(supplier, "supplier").get(), "supplier.get()");
  }

  @Nullable
  private static MethodHandle findGetHeadersMethod(MethodType methodType) {
    try {
      return MethodHandles.lookup().findVirtual(HttpHeaders.class, "get", methodType);
    } catch (Throwable t) {
      return null;
    }
  }

  // before spring web 7.0 HttpHeaders implements Map<String, List<String>>, this triggers
  // errorprone BadInstanceof warning since errorpone is not aware that this instanceof check does
  // not pass with spring web 7.0+
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
