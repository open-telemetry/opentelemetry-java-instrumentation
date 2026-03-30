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
    try {
      return lookup.findVirtual(Message.class, "getAttributes", methodType(Map.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // changed the return type to ConcurrentMap in version 2.1
      try {
        return lookup.findVirtual(Message.class, "getAttributes", methodType(ConcurrentMap.class));
      } catch (NoSuchMethodException | IllegalAccessException f) {
        return null;
      }
    }
  }

  @Nullable
  private static MethodHandle findSetValue() {
    Class<?> setValueReturnType;
    try {
      // changed the generic bound to NamedValue in version 2.1; earlier than that it's Parameter
      setValueReturnType = Class.forName("org.restlet.util.NamedValue");
    } catch (ClassNotFoundException e) {
      try {
        setValueReturnType = Class.forName("org.restlet.data.Parameter");
      } catch (ClassNotFoundException f) {
        return null;
      }
    }

    try {
      return MethodHandles.lookup()
          .findVirtual(
              Series.class, "set", methodType(setValueReturnType, String.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }

  @Nullable
  private static Class<?> findHeaderClass() {
    try {
      // restlet 2.3+
      return Class.forName("org.restlet.data.Header");
    } catch (ClassNotFoundException e) {
      try {
        // restlet 2.1-2.2
        return Class.forName("org.restlet.engine.header.Header");
      } catch (ClassNotFoundException f) {
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
    } catch (NoSuchMethodException | IllegalAccessException e) {
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
    } catch (Throwable e) {
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
    } catch (Throwable e) {
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
