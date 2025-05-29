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

  private static final Class<?> HTTP_REQUEST_CLASS;
  private static final MethodHandle GET_HTTP_CALL;
  private static final MethodHandle GET_SERVER_ADDRESS;

  static {
    Class<?> httpRequestClass = null;
    Class<?> serverCallClass = null;
    MethodHandle getHttpCall = null;
    MethodHandle getServerAddress = null;

    try {
      httpRequestClass = Class.forName("org.restlet.engine.http.HttpRequest");
    } catch (ClassNotFoundException e) {
      // moved to another package in version 2.4
      try {
        httpRequestClass = Class.forName("org.restlet.engine.adapter.HttpRequest");
      } catch (ClassNotFoundException ex) {
        // ignored
      }
    }

    try {
      serverCallClass = Class.forName("org.restlet.engine.http.ServerCall");
    } catch (ClassNotFoundException e) {
      // moved to another package in version 2.4
      try {
        serverCallClass = Class.forName("org.restlet.engine.adapter.ServerCall");
      } catch (ClassNotFoundException ex) {
        // ignored
      }
    }

    if (httpRequestClass != null && serverCallClass != null) {
      try {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        getHttpCall =
            lookup.findVirtual(httpRequestClass, "getHttpCall", methodType(serverCallClass));
        getServerAddress =
            lookup.findVirtual(serverCallClass, "getServerAddress", methodType(String.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // ignored
      }
    }

    HTTP_REQUEST_CLASS = httpRequestClass;
    GET_HTTP_CALL = getHttpCall;
    GET_SERVER_ADDRESS = getServerAddress;
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
    } catch (Throwable e) {
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
      } catch (Throwable e) {
        return null;
      }
    }
    return null;
  }

  private ServerCallAccess() {}
}
