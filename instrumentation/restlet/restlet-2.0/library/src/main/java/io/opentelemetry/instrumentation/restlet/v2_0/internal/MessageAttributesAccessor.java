/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import org.restlet.Message;
import org.restlet.data.Form;
import org.restlet.util.Series;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MessageAttributesAccessor {

  private static final MethodHandle GET_ATTRIBUTES = findGetAttributes();
  private static final MethodHandle SET_VALUE = findSetValue();
  private static final Class<?> HEADER_CLASS = findHeaderClass();
  private static final MethodHandle NEW_SERIES = findNewSeries();

  @Nullable
  private static MethodHandle findGetAttributes() {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    // The return type changed to ConcurrentMap in 2.1.
    for (Class<?> returnType : new Class<?>[] {Map.class, ConcurrentMap.class}) {
      try {
        return lookup.findVirtual(Message.class, "getAttributes", methodType(returnType));
      } catch (NoSuchMethodException | IllegalAccessException ignored) {
        // Try the other signature.
      }
    }
    return null;
  }

  @Nullable
  private static MethodHandle findSetValue() {
    // The generic bound changed from Parameter to NamedValue in 2.1.
    for (String typeName :
        new String[] {"org.restlet.util.NamedValue", "org.restlet.data.Parameter"}) {
      try {
        Class<?> returnType = Class.forName(typeName, false, Series.class.getClassLoader());
        return MethodHandles.lookup()
            .findVirtual(Series.class, "set", methodType(returnType, String.class, String.class));
      } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        // Try the other signature.
      } catch (IllegalAccessException ignored) {
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static Class<?> findHeaderClass() {
    try {
      // restlet 2.3+
      return Class.forName("org.restlet.data.Header");
    } catch (ClassNotFoundException ignored) {
      try {
        // restlet 2.1-2.2
        return Class.forName("org.restlet.engine.header.Header");
      } catch (ClassNotFoundException ignore) {
        // restlet 2.0 does not have Header
        return null;
      }
    }
  }

  @Nullable
  private static MethodHandle findNewSeries() {
    if (HEADER_CLASS == null) {
      return null;
    }
    try {
      // restlet 2.1+ Series has different constructor
      return MethodHandles.lookup()
          .findConstructor(Series.class, methodType(void.class, Class.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      return null;
    }
  }

  @SuppressWarnings("unchecked") // casting result of MethodHandle.invoke
  @Nullable
  public static Map<String, Object> getAttributes(Message message) {
    if (GET_ATTRIBUTES == null) {
      return null;
    }
    try {
      return (Map<String, Object>) GET_ATTRIBUTES.invoke(message);
    } catch (Throwable ignored) {
      return null;
    }
  }

  @Nullable
  public static Series<?> createHeaderSeries() {
    if (HEADER_CLASS == null) {
      return new Form();
    }
    if (NEW_SERIES == null) {
      // should never really happen
      return null;
    }
    try {
      return (Series<?>) NEW_SERIES.invoke(HEADER_CLASS);
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static void setSeriesValue(Series<?> series, String name, String value) {
    if (SET_VALUE == null) {
      return;
    }
    try {
      SET_VALUE.invoke(series, name, value);
    } catch (Throwable ignored) {
      // just do nothing
    }
  }

  private MessageAttributesAccessor() {}
}
