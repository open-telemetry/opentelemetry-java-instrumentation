/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.datastax.driver.core.Session;
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CassandraClientInstrumentationModule extends InstrumentationModule {
  public CassandraClientInstrumentationModule() {
    super("cassandra", "cassandra-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ClusterManagerInstrumentation());
  }

  private static final class ClusterManagerInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      // Note: Cassandra has a large driver and we instrument single class in it.
      // The rest is ignored in AdditionalLibraryIgnoresMatcher
      return named("com.datastax.driver.core.Cluster$Manager");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPrivate()).and(named("newSession")).and(takesArguments(0)),
          CassandraClientInstrumentationModule.class.getName() + "$CassandraClientAdvice");
    }
  }

  public static class CassandraClientAdvice {
    /**
     * Strategy: each time we build a connection to a Cassandra cluster, the
     * com.datastax.driver.core.Cluster$Manager.newSession() method is called. The opentracing
     * contribution is a simple wrapper, so we just have to wrap the new session.
     *
     * @param session The fresh session to patch. This session is replaced with new session
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
