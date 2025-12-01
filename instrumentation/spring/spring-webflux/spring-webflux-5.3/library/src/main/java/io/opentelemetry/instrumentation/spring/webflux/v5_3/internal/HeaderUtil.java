/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.springframework.http.HttpHeaders;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class HeaderUtil {

  @Nullable private static final MethodHandle GET_HEADERS;
  @Nullable private static final MethodHandle FOR_EACH;
  @Nullable private static final MethodHandle KEY_SET;

  static {
    GET_HEADERS =
        firstAvailableHandle(
            findGetHeadersMethod(
                MethodType.methodType(List.class, String.class)), // since spring web 7.0
            () ->
                findGetHeadersMethod(
                    MethodType.methodType(List.class, Object.class))); // before spring web 7.0

    // Spring Web 7+
    MethodHandle forEach = null;
    try {
      forEach =
          MethodHandles.lookup()
              .findVirtual(
                  HttpHeaders.class,
                  "forEach",
                  MethodType.methodType(void.class, BiConsumer.class));
    } catch (Throwable t) {
      // ignore - will fall back to keySet
    }
    FOR_EACH = forEach;

    // Spring Web 6 and earlier
    MethodHandle keySet = null;
    if (FOR_EACH == null) {
      try {
        keySet =
            MethodHandles.lookup()
                .findVirtual(Map.class, "keySet", MethodType.methodType(Set.class));
      } catch (Throwable t) {
        // ignore
      }
    }
    KEY_SET = keySet;
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
  public static List<String> getHeader(HttpHeaders headers, String name) {
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

  @SuppressWarnings("unchecked") // HttpHeaders is a Map in Spring Web 6 and earlier
  public static Set<String> getKeys(HttpHeaders headers) {
    if (FOR_EACH != null) {
      // Spring Web 7: HttpHeaders has forEach(BiConsumer) method
      try {
        Set<String> keys = new HashSet<>();
        FOR_EACH.invoke(headers, (BiConsumer<String, ?>) (key, value) -> keys.add(key));
        return keys;
      } catch (Throwable t) {
        // ignore
      }
    } else if (KEY_SET != null) {
      // Spring Web 6 and earlier: HttpHeaders extends Map
      try {
        return (Set<String>) KEY_SET.invoke(headers);
      } catch (Throwable t) {
        // ignore
      }
    }
    return emptySet();
  }

  private HeaderUtil() {}
}
