/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import org.apache.pekko.dispatch.Envelope;
import org.apache.pekko.dispatch.sysmsg.SystemMessage;

public class VirtualFields {

  public static final VirtualField<Envelope, PropagatedContext> ENVELOPE_PROPAGATED_CONTEXT =
      VirtualField.find(Envelope.class, PropagatedContext.class);

  public static final VirtualField<SystemMessage, PropagatedContext>
      SYSTEM_MESSAGE_PROPAGATED_CONTEXT =
          VirtualField.find(SystemMessage.class, PropagatedContext.class);

  private VirtualFields() {}
}
