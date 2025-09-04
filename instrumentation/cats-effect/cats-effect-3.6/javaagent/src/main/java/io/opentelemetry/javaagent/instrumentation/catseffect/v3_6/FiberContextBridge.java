/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.catseffect.v3_6;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.catseffect.v3_6.FiberLocalContextHelper;
import javax.annotation.Nullable;

public class FiberContextBridge implements ContextStorage {

  private final ContextStorage agentContextStorage;

  public FiberContextBridge(ContextStorage delegate) {
    this.agentContextStorage = delegate;
  }

  @Override
  public Scope attach(Context toAttach) {
    if (FiberLocalContextHelper.isUnderFiberContext()) {
      return FiberLocalContextHelper.attach(toAttach);
    } else {
      return agentContextStorage.attach(toAttach);
    }
  }

  @Nullable
  @Override
  public Context current() {
    if (FiberLocalContextHelper.isUnderFiberContext()) {
      return FiberLocalContextHelper.current();
    } else {
      return agentContextStorage.current();
    }
  }
}
