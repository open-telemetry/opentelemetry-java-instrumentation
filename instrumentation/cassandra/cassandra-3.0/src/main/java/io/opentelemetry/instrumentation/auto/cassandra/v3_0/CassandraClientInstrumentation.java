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

package io.opentelemetry.instrumentation.auto.cassandra.v3_0;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.datastax.driver.core.Session;
import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class CassandraClientInstrumentation extends Instrumenter.Default {

  public CassandraClientInstrumentation() {
    super("cassandra");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Note: Cassandra has a large driver and we instrument single class in it.
    // The rest is ignored in AdditionalLibraryIgnoresMatcher
    return named("com.datastax.driver.core.Cluster$Manager");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".CassandraDatabaseClientTracer",
      packageName + ".TracingSession",
      packageName + ".TracingSession$1",
      packageName + ".TracingSession$2",
      "io.opentelemetry.auto.common.exec.DaemonThreadFactory",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPrivate()).and(named("newSession")).and(takesArguments(0)),
        CassandraClientInstrumentation.class.getName() + "$CassandraClientAdvice");
  }

  public static class CassandraClientAdvice {
    /**
     * Strategy: each time we build a connection to a Cassandra cluster, the
     * com.datastax.driver.core.Cluster$Manager.newSession() method is called. The opentracing
     * contribution is a simple wrapper, so we just have to wrap the new session.
     *
     * @param session The fresh session to patch. This session is replaced with new session
     * @throws Exception
     */
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void injectTracingSession(@Advice.Return(readOnly = false) Session session) {
      // This should cover ours and OT's TracingSession
      if (session.getClass().getName().endsWith("cassandra.TracingSession")) {
        return;
      }
      session = new TracingSession(session);
    }
  }
}
