/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static java.util.Collections.emptyList;

import io.vertx.redis.client.Request;
import java.lang.reflect.Method;
import java.util.List;

public final class RequestAccess {

  private static final String REQUEST_IMPL = "io.vertx.redis.client.impl.RequestImpl";

  private static volatile Method getArgsMethod;

  // reflectively invoked package-private RequestImpl#getArgs returns List<byte[]>
  @SuppressWarnings("unchecked")
  public static List<byte[]> getArgs(Request request) {
    if (request == null || !REQUEST_IMPL.equals(request.getClass().getName())) {
      return emptyList();
    }
    try {
      Method method = getArgsMethod;
      if (method == null) {
        method = request.getClass().getDeclaredMethod("getArgs");
        method.setAccessible(true);
        getArgsMethod = method;
      }
      return (List<byte[]>) method.invoke(request);
    } catch (Throwable e) {
      return emptyList();
    }
  }

  private RequestAccess() {}
}
