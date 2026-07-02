/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.thrift.server;

import org.apache.thrift.transport.TTransport;

// Helper for accessing a non-public field.
public class FrameBufferUtil {

  public static TTransport getTransport(AbstractNonblockingServer.FrameBuffer frameBuffer) {
    return frameBuffer.trans_;
  }

  private FrameBufferUtil() {}
}
