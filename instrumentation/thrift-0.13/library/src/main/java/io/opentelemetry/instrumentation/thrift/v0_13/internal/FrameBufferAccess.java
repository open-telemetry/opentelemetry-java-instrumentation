/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import java.lang.reflect.Field;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.thrift.server.AbstractNonblockingServer;
import org.apache.thrift.transport.TTransport;

/**
 * Reflective accessor for the non-public {@code AbstractNonblockingServer.FrameBuffer#trans_}
 * field.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class FrameBufferAccess {

  private static final Logger logger = Logger.getLogger(FrameBufferAccess.class.getName());

  @Nullable private static final Field transField = findTransField();

  @Nullable
  private static Field findTransField() {
    try {
      Field field = AbstractNonblockingServer.FrameBuffer.class.getDeclaredField("trans_");
      field.setAccessible(true);
      return field;
    } catch (Throwable t) {
      logger.log(WARNING, "Failed to locate AbstractNonblockingServer.FrameBuffer#trans_ field", t);
      return null;
    }
  }

  @Nullable
  public static TTransport getTransport(AbstractNonblockingServer.FrameBuffer frameBuffer) {
    if (transField == null) {
      return null;
    }
    try {
      return (TTransport) transField.get(frameBuffer);
    } catch (Throwable t) {
      logger.log(FINE, "Failed to read AbstractNonblockingServer.FrameBuffer#trans_ field", t);
      return null;
    }
  }

  private FrameBufferAccess() {}
}
