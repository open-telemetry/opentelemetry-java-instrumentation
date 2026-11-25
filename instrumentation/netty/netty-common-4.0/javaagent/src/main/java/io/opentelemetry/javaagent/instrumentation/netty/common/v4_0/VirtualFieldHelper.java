/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.v4_0;

import io.netty.channel.ChannelHandler;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.ref.WeakReference;
import javax.annotation.Nullable;

public class VirtualFieldHelper {

  // The pipeline owns the instrumentation handler, which may reference the original handler.
  private static final VirtualField<ChannelHandler, WeakReference<ChannelHandler>> CHANNEL_HANDLER =
      VirtualField.find(ChannelHandler.class, WeakReference.class);

  @Nullable
  public static ChannelHandler getInstrumentationHandler(ChannelHandler handler) {
    WeakReference<ChannelHandler> reference = CHANNEL_HANDLER.get(handler);
    return reference == null ? null : reference.get();
  }

  public static void setInstrumentationHandler(
      ChannelHandler handler, @Nullable ChannelHandler instrumentationHandler) {
    CHANNEL_HANDLER.set(
        handler, instrumentationHandler == null ? null : new WeakReference<>(instrumentationHandler));
  }

  private VirtualFieldHelper() {}
}
