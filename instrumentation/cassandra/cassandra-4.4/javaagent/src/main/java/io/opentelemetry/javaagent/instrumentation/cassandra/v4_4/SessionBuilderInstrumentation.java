/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import static io.opentelemetry.javaagent.instrumentation.cassandra.v4_4.CassandraSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.datastax.oss.driver.api.core.metadata.EndPoint;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SessionBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Note: Cassandra has a large driver and we instrument single class in it.
    // The rest is ignored in AdditionalLibraryIgnoresMatcher
    return named("com.datastax.oss.driver.api.core.session.SessionBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(named("buildAsync")).and(takesArguments(0)),
        getClass().getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    /**
     * Strategy: each time we build a connection to a Cassandra cluster, the
     * com.datastax.oss.driver.api.core.session.SessionBuilder.buildAsync() method is called. The
     * OpenTelemetry instrumentation is a simple wrapper, so we just have to wrap the new session.
     *
     * @param stage The fresh CompletionStage to patch. This stage produces session which is
     *     replaced with new session
     */
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static CompletionStage<?> injectTracingSession(
        @Advice.Return CompletionStage<?> stage,
        @Advice.FieldValue("programmaticContactPoints") Set<EndPoint> programmaticContactPoints) {
      return stage.thenApply(
          new CompletionStageFunction(telemetry(), new HashSet<>(programmaticContactPoints)));
    }
  }
}
