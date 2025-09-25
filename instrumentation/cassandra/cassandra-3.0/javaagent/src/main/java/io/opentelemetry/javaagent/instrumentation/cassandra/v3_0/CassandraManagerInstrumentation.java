/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.datastax.driver.core.Session;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class CassandraManagerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Note: Cassandra has a large driver and we instrument single class in it.
    // The rest is ignored in AdditionalLibraryIgnoresMatcher
    return named("com.datastax.driver.core.Cluster$Manager");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPrivate()).and(named("newSession")).and(takesArguments(0)),
        this.getClass().getName() + "$NewSessionAdvice");
  }

  @SuppressWarnings("unused")
  public static class NewSessionAdvice {

    /**
     * Strategy: each time we build a connection to a Cassandra cluster, the
     * com.datastax.driver.core.Cluster$Manager.newSession() method is called. The opentracing
     * contribution is a simple wrapper, so we just have to wrap the new session.
     *
     * @param session The fresh session to patch. This session is replaced with new session
     */
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Session injectTracingSession(@Advice.Return Session session) {
      // This should cover ours and OT's TracingSession
      if (session.getClass().getName().endsWith("cassandra.TracingSession")) {
        return session;
      }
      return new TracingSession(session);
    }
  }
}
