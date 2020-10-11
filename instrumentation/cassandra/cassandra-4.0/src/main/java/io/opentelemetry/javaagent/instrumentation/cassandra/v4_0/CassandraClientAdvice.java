/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import java.util.concurrent.CompletionStage;
import net.bytebuddy.asm.Advice;

public class CassandraClientAdvice {
  /**
   * Strategy: each time we build a connection to a Cassandra cluster, the
   * com.datastax.oss.driver.api.core.session.SessionBuilder.buildAsync() method is called. The
   * opentracing contribution is a simple wrapper, so we just have to wrap the new session.
   *
   * @param stage The fresh CompletionStage to patch. This stage produces session which is replaced
   *     with new session
   */
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void injectTracingSession(
      @Advice.Return(readOnly = false) CompletionStage<?> stage) {
    stage = stage.thenApply(new CompletionStageFunction());
  }
}
