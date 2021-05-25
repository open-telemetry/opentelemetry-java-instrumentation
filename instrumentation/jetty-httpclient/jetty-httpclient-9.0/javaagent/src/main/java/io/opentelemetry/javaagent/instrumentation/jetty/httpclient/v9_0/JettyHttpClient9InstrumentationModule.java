/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_0;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_0.JettyHttpClient9Tracer.tracer;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.client.api.Request;

@AutoService(InstrumentationModule.class)
public class JettyHttpClient9InstrumentationModule extends InstrumentationModule {

  public JettyHttpClient9InstrumentationModule() {
    super("jetty-httpclient", "jetty-httpclient-9");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JettyHttpClient9Instrumentation());
  }

  public static class JettyHttpClient9Instrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("org.eclipse.jetty.client.api.Request$BeginListener");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return hasSuperType(named("org.eclipse.jetty.client.api.Request").and(isInterface()));
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          isMethod().and(named("send")),
          JettyHttpClient9InstrumentationModule.class.getName() + "$JettyHttpClient9Advice");
    }
  }

  public static class JettyHttpClient9Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addTracingEnter(
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.This Request jettyRequest) {

      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      JettyHttpClient9TracingInterceptor interceptor =
          new JettyHttpClient9TracingInterceptor(parentContext);
      interceptor.attachToRequest(jettyRequest);

      scope = interceptor.getCtx().makeCurrent();
      context = interceptor.getCtx();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void exitTracingInterceptor(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      }

      if (scope != null) {
        scope.close();
      }
    }
  }
}
