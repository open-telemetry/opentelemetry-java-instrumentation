/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.v4_0;

import io.netty.channel.ChannelHandler;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public class VirtualFieldHelper {

  public static final VirtualField<ChannelHandler, ChannelHandler> CHANNEL_HANDLER =
      VirtualField.find(ChannelHandler.class, ChannelHandler.class);

  private VirtualFieldHelper() {}
}
