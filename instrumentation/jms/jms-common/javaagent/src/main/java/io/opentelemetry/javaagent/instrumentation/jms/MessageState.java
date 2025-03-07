/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class MessageState {

  public final Context context;
  public final Scope processScope;
  public final MessageWithDestination message;

  public MessageState(Context context, Scope processScope, MessageWithDestination message) {
    this.context = context;
    this.processScope = processScope;
    this.message = message;
  }
}
