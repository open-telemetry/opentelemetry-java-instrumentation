/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import java.util.concurrent.Executor;

class DirectExecutor implements Executor {

  static final Executor INSTANCE = new DirectExecutor();

  @Override
  public void execute(Runnable command) {
    command.run();
  }
}
