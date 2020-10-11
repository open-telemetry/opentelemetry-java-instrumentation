/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.khttp;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class KHttpInstrumentation extends Instrumenter.Default {

  public KHttpInstrumentation() {
    super("khttp");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("khttp.KHttp");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("khttp.KHttp"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".KHttpHeadersInjectAdapter",
      packageName + ".KHttpTracer",
      packageName + ".RequestWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(not(isAbstract()))
            .and(named("request"))
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("java.lang.String")))
            .and(takesArgument(2, named("java.util.Map")))
            .and(returns(named("khttp.responses.Response"))),
        packageName + ".KHttpAdvice");
  }
}
