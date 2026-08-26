/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.pekko.remote.artery.InboundEnvelope;
import org.apache.pekko.remote.artery.OutboundEnvelope;

public final class VirtualFields {

  /** Context that was current when the message was handed to the artery outbound stream. */
  public static final VirtualField<OutboundEnvelope, Context> OUTBOUND_ENVELOPE_CONTEXT =
      VirtualField.find(OutboundEnvelope.class, Context.class);

  /** Context that was extracted from the metadata of a received message. */
  public static final VirtualField<InboundEnvelope, Context> INBOUND_ENVELOPE_CONTEXT =
      VirtualField.find(InboundEnvelope.class, Context.class);

  private VirtualFields() {}
}
