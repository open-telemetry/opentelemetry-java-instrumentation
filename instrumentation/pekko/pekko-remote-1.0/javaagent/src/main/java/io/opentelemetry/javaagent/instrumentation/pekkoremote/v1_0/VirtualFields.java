/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import java.nio.ByteBuffer;
import org.apache.pekko.remote.artery.InboundEnvelope;
import org.apache.pekko.remote.artery.OutboundEnvelope;

public class VirtualFields {

  public static final VirtualField<
          ByteBuffer,
          PekkoRemoteArteryRemoteInstrumentsCompanionInstrumentation.CreateAdvice
              .PekkoRemoteMetadata>
      BYTE_BUFFER_OTEL_METADATA =
          VirtualField.find(
              ByteBuffer.class,
              PekkoRemoteArteryRemoteInstrumentsCompanionInstrumentation.CreateAdvice
                  .PekkoRemoteMetadata.class);

  public static final VirtualField<InboundEnvelope, PropagatedContext>
      INBOUND_ENVELOPE_PROPAGATED_CONTEXT =
          VirtualField.find(InboundEnvelope.class, PropagatedContext.class);

  public static final VirtualField<OutboundEnvelope, PropagatedContext>
      OUTBOUND_ENVELOPE_PROPAGATED_CONTEXT =
          VirtualField.find(OutboundEnvelope.class, PropagatedContext.class);

  private VirtualFields() {}
}
