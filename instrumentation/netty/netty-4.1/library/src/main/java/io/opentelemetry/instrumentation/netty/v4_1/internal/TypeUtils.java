/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class TypeUtils {

  private TypeUtils() {}

  public static boolean isLastHttpContent(Object obj) {
    Class<?> objClass = obj.getClass();
    if (objClass == DefaultLastHttpContent.class) {
      return true;
    }

    return obj instanceof LastHttpContent;
  }

  public static boolean isFullHttpResponse(Object obj) {
    Class<?> objClass = obj.getClass();
    if (objClass == DefaultFullHttpResponse.class) {
      return true;
    }

    return obj instanceof FullHttpResponse;
  }

  public static boolean isHttpResponse(Object obj) {
    Class<?> objClass = obj.getClass();
    if (objClass == DefaultFullHttpResponse.class || objClass == DefaultHttpResponse.class) {
      return true;
    }

    return obj instanceof HttpResponse;
  }

  public static boolean isHttpRequest(Object msg) {
    Class<?> objClass = msg.getClass();
    if (objClass == DefaultFullHttpRequest.class || objClass == DefaultHttpRequest.class) {
      return true;
    }

    return msg instanceof HttpRequest;
  }
}
