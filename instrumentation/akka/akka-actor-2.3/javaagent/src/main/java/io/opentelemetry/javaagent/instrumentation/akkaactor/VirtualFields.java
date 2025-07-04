/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import akka.dispatch.Envelope;
import akka.dispatch.sysmsg.SystemMessage;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;

public class VirtualFields {

  private VirtualFields() {}

  public static final VirtualField<Envelope, PropagatedContext> ENVELOPE_PROPAGATED_CONTEXT =
      VirtualField.find(Envelope.class, PropagatedContext.class);
  public static final VirtualField<SystemMessage, PropagatedContext>
      SYSTEM_MESSAGE_PROPAGATED_CONTEXT =
          VirtualField.find(SystemMessage.class, PropagatedContext.class);
}
