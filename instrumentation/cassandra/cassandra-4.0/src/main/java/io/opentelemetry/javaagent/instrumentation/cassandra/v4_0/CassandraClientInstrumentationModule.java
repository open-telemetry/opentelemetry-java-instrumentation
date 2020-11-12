/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class CassandraClientInstrumentationModule extends InstrumentationModule {
  public CassandraClientInstrumentationModule() {
    super("cassandra");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".CassandraQueryNormalizer",
      packageName + ".CassandraDatabaseClientTracer",
      packageName + ".TracingCqlSession",
      packageName + ".CompletionStageFunction"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new SessionBuilderInstrumentation());
  }

  private static final class SessionBuilderInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      // Note: Cassandra has a large driver and we instrument single class in it.
      // The rest is ignored in AdditionalLibraryIgnoresMatcher
      return named("com.datastax.oss.driver.api.core.session.SessionBuilder");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(named("buildAsync")).and(takesArguments(0)),
          CassandraClientAdvice.class.getName());
    }
  }
}
