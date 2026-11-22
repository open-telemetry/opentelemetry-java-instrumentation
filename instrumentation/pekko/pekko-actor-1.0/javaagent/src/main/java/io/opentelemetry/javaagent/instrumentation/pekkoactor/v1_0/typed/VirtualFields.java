/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.pekko.actor.typed.internal.StashBufferImpl;

public class VirtualFields {

  // a class literal can only be raw, the type parameter of the node is not needed
  @SuppressWarnings("rawtypes")
  public static final VirtualField<StashBufferImpl.Node, Context> STASH_NODE_CONTEXT =
      VirtualField.find(StashBufferImpl.Node.class, Context.class);

  private VirtualFields() {}
}
