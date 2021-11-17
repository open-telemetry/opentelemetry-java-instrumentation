/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.util;

import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import org.jboss.netty.channel.ChannelHandler;

public final class HttpSchemeUtil {

  private static final Class<? extends ChannelHandler> sslHandlerClass = getSslHandlerClass();

  @SuppressWarnings("unchecked")
  private static Class<? extends ChannelHandler> getSslHandlerClass() {
    try {
      return (Class<? extends ChannelHandler>)
          Class.forName(
              "org.jboss.netty.handler.ssl.SslHandler",
              false,
              HttpSchemeUtil.class.getClassLoader());
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  public static String getScheme(HttpRequestAndChannel requestAndChannel) {
    return isHttps(requestAndChannel) ? "https" : "http";
  }

  private static boolean isHttps(HttpRequestAndChannel requestAndChannel) {
    return sslHandlerClass != null
        && requestAndChannel.channel().getPipeline().get(sslHandlerClass) != null;
  }

  private HttpSchemeUtil() {}
}
