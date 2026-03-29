/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import io.lettuce.core.protocol.AsyncCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public class LettuceSingletons {

  public static final VirtualField<AsyncCommand<?, ?, ?>, Context> CONTEXT =
      VirtualField.find(AsyncCommand.class, Context.class);

  private LettuceSingletons() {}
}
