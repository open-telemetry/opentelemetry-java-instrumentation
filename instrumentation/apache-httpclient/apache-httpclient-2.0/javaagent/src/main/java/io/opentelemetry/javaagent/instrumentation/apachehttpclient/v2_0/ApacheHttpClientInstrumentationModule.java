/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0.CommonsHttpClientTracer.tracer;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.httpclient.HttpMethod;

@AutoService(InstrumentationModule.class)
public class ApacheHttpClientInstrumentationModule extends InstrumentationModule {

  public ApacheHttpClientInstrumentationModule() {
    super("apache-httpclient", "apache-httpclient-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpClientInstrumentation());
  }

  public static class HttpClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("org.apache.commons.httpclient.HttpClient");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return extendsClass(named("org.apache.commons.httpclient.HttpClient"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(named("executeMethod"))
              .and(takesArguments(3))
              .and(takesArgument(1, named("org.apache.commons.httpclient.HttpMethod"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$ExecAdvice");
    }
  }

  public static class ExecAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(1) HttpMethod httpMethod,
        @Advice.Local("otelOperation") HttpClientOperation operation,
        @Advice.Local("otelScope") Scope scope) {
      operation = tracer().startOperation(httpMethod);
      scope = operation.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(1) HttpMethod httpMethod,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelOperation") HttpClientOperation operation,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      tracer().endMaybeExceptionally(operation, httpMethod, throwable);
    }
  }
}
