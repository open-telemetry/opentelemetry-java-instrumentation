/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * Exposes and propagates the {@link ChannelInitializer#initChannel(Channel)} call for a wrapped
 * {@link ChannelInitializer} instance.
 *
 * <p>{@code initChannel} is {@code protected} on {@link ChannelInitializer}. Overriding it from any
 * package is legal, but invoking it on a different instance whose static type is {@link
 * ChannelInitializer} itself requires either same-package access or reflection (JLS 6.6.2). This
 * class uses a cached {@link Method} so it can live in a normal {@code io.opentelemetry.*} package.
 */
abstract class OpenTelemetryChannelInitializerDelegate<T extends Channel>
    extends ChannelInitializer<T> {

  @Nullable private static final Method INIT_CHANNEL_METHOD = resolveInitChannelMethod();

  @Nullable
  private static Method resolveInitChannelMethod() {
    try {
      Method method = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
      method.setAccessible(true);
      return method;
    } catch (Throwable t) {
      return null;
    }
  }

  static boolean isSupported() {
    return INIT_CHANNEL_METHOD != null;
  }

  private final ChannelInitializer<T> initializer;

  OpenTelemetryChannelInitializerDelegate(ChannelInitializer<T> initializer) {
    this.initializer = initializer;
  }

  @Override
  protected void initChannel(T t) throws Exception {
    Method initChannelMethod = INIT_CHANNEL_METHOD;
    if (initChannelMethod == null) {
      return;
    }
    try {
      initChannelMethod.invoke(initializer, t);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new IllegalStateException(cause);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
