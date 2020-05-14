/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.cassandra.v4_0;

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
