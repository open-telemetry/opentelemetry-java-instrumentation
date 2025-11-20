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
        nonNullHandle(
            findGetHeadersMethod(
                MethodType.methodType(List.class, String.class)), // since spring web 7.0
            () ->
                findGetHeadersMethod(
                    MethodType.methodType(List.class, Object.class))); // before spring web 7.0
  }

  private static MethodHandle nonNullHandle(
      @Nullable MethodHandle first, Supplier<? extends MethodHandle> supplier) {
    return (first != null)
        ? first
        : requireNonNull(supplier.get(), "Could not find suitable get method on HttpHeaders");
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
  @SuppressWarnings("unchecked") // casting GET_HEADERS.invoke result
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
