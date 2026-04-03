/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import javax.annotation.Nullable;
import org.restlet.Request;

final class ServerCallAccess {

  private static final Class<?> HTTP_REQUEST_CLASS = findHttpRequestClass();
  private static final Class<?> SERVER_CALL_CLASS = findServerCallClass();
  private static final MethodHandle GET_HTTP_CALL = findGetHttpCall();
  private static final MethodHandle GET_SERVER_ADDRESS = findGetServerAddress();

  @Nullable
  private static Class<?> findHttpRequestClass() {
    try {
      return Class.forName("org.restlet.engine.http.HttpRequest");
    } catch (ClassNotFoundException ignored) {
      // moved to another package in version 2.4
      try {
        return Class.forName("org.restlet.engine.adapter.HttpRequest");
      } catch (ClassNotFoundException ignore) {
        return null;
      }
    }
  }

  @Nullable
  private static Class<?> findServerCallClass() {
    try {
      return Class.forName("org.restlet.engine.http.ServerCall");
    } catch (ClassNotFoundException ignored) {
      // moved to another package in version 2.4
      try {
        return Class.forName("org.restlet.engine.adapter.ServerCall");
      } catch (ClassNotFoundException ignore) {
        return null;
      }
    }
  }

  @Nullable
  private static MethodHandle findGetHttpCall() {
    if (HTTP_REQUEST_CLASS == null || SERVER_CALL_CLASS == null) {
      return null;
    }
    try {
      return MethodHandles.publicLookup()
          .findVirtual(HTTP_REQUEST_CLASS, "getHttpCall", methodType(SERVER_CALL_CLASS));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      return null;
    }
  }

  @Nullable
  private static MethodHandle findGetServerAddress() {
    if (SERVER_CALL_CLASS == null) {
      return null;
    }
    try {
      return MethodHandles.publicLookup()
          .findVirtual(SERVER_CALL_CLASS, "getServerAddress", methodType(String.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      return null;
    }
  }

  @Nullable
  static String getServerAddress(Request request) {
    if (GET_SERVER_ADDRESS == null) {
      return null;
    }
    Object call = serverCall(request);
    if (call == null) {
      return null;
    }
    try {
      return (String) GET_SERVER_ADDRESS.invoke(call);
    } catch (Throwable ignored) {
      return null;
    }
  }

  @Nullable
  private static Object serverCall(Request request) {
    if (GET_HTTP_CALL == null || HTTP_REQUEST_CLASS == null) {
      return null;
    }
    if (HTTP_REQUEST_CLASS.isInstance(request)) {
      try {
        return GET_HTTP_CALL.invoke(request);
      } catch (Throwable ignored) {
        return null;
      }
    }
    return null;
  }

  private ServerCallAccess() {}
}
