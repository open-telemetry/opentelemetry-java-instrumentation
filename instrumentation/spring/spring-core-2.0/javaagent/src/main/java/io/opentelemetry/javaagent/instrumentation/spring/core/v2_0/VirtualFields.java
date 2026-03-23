/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.core.v2_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;

public class VirtualFields {

  public static final VirtualField<Runnable, PropagatedContext> PROPAGATED_CONTEXT =
      VirtualField.find(Runnable.class, PropagatedContext.class);

  private VirtualFields() {}
}
