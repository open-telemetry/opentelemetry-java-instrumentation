/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.pekko.remote.EndpointManager;
import org.apache.pekko.remote.WireFormats;

public final class VirtualFields {

  /** Context that was current when the message was handed to remoting. */
  public static final VirtualField<EndpointManager.Send, Context> SEND_CONTEXT =
      VirtualField.find(EndpointManager.Send.class, Context.class);

  /**
   * Context that was received with a message. The message that is decoded is not what the
   * dispatcher is called with, both have the serialized message.
   */
  public static final VirtualField<WireFormats.SerializedMessage, Context>
      SERIALIZED_MESSAGE_CONTEXT =
          VirtualField.find(WireFormats.SerializedMessage.class, Context.class);

  private VirtualFields() {}
}
