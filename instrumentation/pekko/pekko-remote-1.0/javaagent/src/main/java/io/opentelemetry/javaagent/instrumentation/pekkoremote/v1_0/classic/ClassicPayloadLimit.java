/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic;

import javax.annotation.Nullable;
import org.apache.pekko.remote.EndpointActor;
import org.apache.pekko.remote.transport.PekkoProtocolTransport;

/**
 * The largest pdu that the endpoint currently writing a message is allowed to produce. {@code
 * EndpointWriter.writeSend} discards a pdu that is larger than the transport allows, so appending
 * the context unconditionally would stop a message that used to fit from being delivered at all.
 *
 * <p>The codec that appends the context has no reference to the transport, so the limit is taken
 * where it is available and read back while the same call is still on the stack. {@code writeSend}
 * builds the message on the thread of the endpoint actor, so a thread local holds it.
 */
public final class ClassicPayloadLimit {

  private static final ThreadLocal<Integer> LIMIT = new ThreadLocal<>();

  public static void set(EndpointActor writer) {
    // the declared type of the transport is deprecated along with the rest of classic remoting
    Object transport = writer.transport();
    if (transport instanceof PekkoProtocolTransport) {
      LIMIT.set(((PekkoProtocolTransport) transport).maximumPayloadBytes());
    }
  }

  public static void clear() {
    LIMIT.remove();
  }

  /** Whether a pdu of the given size may be written, true when no limit is known. */
  public static boolean fits(int size) {
    @Nullable Integer limit = LIMIT.get();
    return limit == null || size <= limit;
  }

  private ClassicPayloadLimit() {}
}
