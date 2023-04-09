/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.twitter.finagle;

import com.twitter.finagle.netty4.transport.ChannelTransport;

/** Exposes the finagle-internal {@link ChannelTransport#HandlerName()}. */
public class ChannelTransportHelpers {
  private ChannelTransportHelpers() {}

  public static String getHandlerName() {
    return ChannelTransport.HandlerName();
  }
}
