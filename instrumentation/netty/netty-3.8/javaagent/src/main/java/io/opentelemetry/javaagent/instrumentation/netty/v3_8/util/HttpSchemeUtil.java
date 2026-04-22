/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.util;

import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequest;
import javax.annotation.Nullable;
import org.jboss.netty.channel.ChannelHandler;

public class HttpSchemeUtil {

  private static final Class<? extends ChannelHandler> sslHandlerClass = getSslHandlerClass();

  @Nullable
  private static Class<? extends ChannelHandler> getSslHandlerClass() {
    try {
      return Class.forName(
              "org.jboss.netty.handler.ssl.SslHandler",
              false,
              HttpSchemeUtil.class.getClassLoader())
          .asSubclass(ChannelHandler.class);
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  public static String getScheme(NettyRequest requestAndChannel) {
    return isHttps(requestAndChannel) ? "https" : "http";
  }

  private static boolean isHttps(NettyRequest requestAndChannel) {
    return sslHandlerClass != null
        && requestAndChannel.channel().getPipeline().get(sslHandlerClass) != null;
  }

  private HttpSchemeUtil() {}
}
