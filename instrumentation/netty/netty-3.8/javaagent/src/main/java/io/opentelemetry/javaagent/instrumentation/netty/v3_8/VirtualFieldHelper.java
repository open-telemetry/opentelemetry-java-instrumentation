/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.jboss.netty.channel.Channel;

public class VirtualFieldHelper {

  public static final VirtualField<Channel, NettyConnectionContext> CONNECTION_CONTEXT =
      VirtualField.find(Channel.class, NettyConnectionContext.class);

  private VirtualFieldHelper() {}
}
