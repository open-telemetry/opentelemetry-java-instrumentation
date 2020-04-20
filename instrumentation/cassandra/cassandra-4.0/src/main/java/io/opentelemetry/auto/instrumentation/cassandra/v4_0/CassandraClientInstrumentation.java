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

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import java.util.concurrent.CompletionStage;
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
    return named("com.datastax.oss.driver.api.core.session.SessionBuilder");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".CassandraClientDecorator",
      packageName + ".TracingCqlSession",
      packageName + ".CompletionStageFunction"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("buildAsync")).and(takesArguments(0)),
        CassandraClientInstrumentation.class.getName() + "$CassandraClientAdvice");
  }

  public static class CassandraClientAdvice {
    /**
     * Strategy: each time we build a connection to a Cassandra cluster, the
     * com.datastax.oss.driver.api.core.session.SessionBuilder.buildAsync() method is called. The
     * opentracing contribution is a simple wrapper, so we just have to wrap the new session.
     *
     * @param stage The fresh CompletionStage to patch. This stage produces session which is
     *     replaced with new session
     */
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void injectTracingSession(
        @Advice.Return(readOnly = false) CompletionStage<?> stage) {
      stage = stage.thenApply(new CompletionStageFunction());
    }
  }
}
