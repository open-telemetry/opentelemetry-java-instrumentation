/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Exposes and propagates the {@link ChannelInitializer#initChannel(Channel)} call for a wrapped
 * {@link ChannelInitializer} instance.
 *
 * <p>{@code initChannel} is {@code protected} on {@link ChannelInitializer}. Overriding it from any
 * package is legal, but invoking it on a different instance whose static type is {@link
 * ChannelInitializer} itself requires either same-package access or reflection (JLS 6.6.2). This
 * class uses a cached {@link Method} handle so it can live in a normal {@code io.opentelemetry.*}
 * package.
 */
public abstract class OpenTelemetryChannelInitializerDelegate<T extends Channel>
    extends ChannelInitializer<T> {

  private static final Method INIT_CHANNEL_METHOD = resolveInitChannelMethod();

  private static Method resolveInitChannelMethod() {
    try {
      Method method = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
      method.setAccessible(true);
      return method;
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("ChannelInitializer#initChannel not found", e);
    }
  }

  private final ChannelInitializer<T> initializer;

  protected OpenTelemetryChannelInitializerDelegate(ChannelInitializer<T> initializer) {
    this.initializer = initializer;
  }

  @Override
  protected void initChannel(T t) throws Exception {
    try {
      INIT_CHANNEL_METHOD.invoke(initializer, t);
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
