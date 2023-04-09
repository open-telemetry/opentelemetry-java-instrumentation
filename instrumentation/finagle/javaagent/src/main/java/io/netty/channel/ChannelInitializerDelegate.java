/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.netty.channel;

/** Exists to correctly expose and propagate the {@link #initChannel(Channel)} calls. */
public abstract class ChannelInitializerDelegate<T extends Channel> extends ChannelInitializer<T> {

  private final ChannelInitializer<T> initializer;

  public ChannelInitializerDelegate(ChannelInitializer<T> initializer) {
    this.initializer = initializer;
  }

  @Override
  protected void initChannel(T t) throws Exception {
    initializer.initChannel(t);
  }
}
