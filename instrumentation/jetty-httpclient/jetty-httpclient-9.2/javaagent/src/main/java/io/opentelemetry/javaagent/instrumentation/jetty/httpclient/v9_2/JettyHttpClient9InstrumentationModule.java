/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2.JettyClientWrapUtil.wrapResponseListeners;
import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2.JettyHttpClientInstrumenters.instrumenter;
import static java.util.Collections.singletonList;
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
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Response;

@AutoService(InstrumentationModule.class)
public class JettyHttpClient9InstrumentationModule extends InstrumentationModule {

  public JettyHttpClient9InstrumentationModule() {
    super("jetty-httpclient", "jetty-httpclient-9.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JettyHttpClient9Instrumentation());
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // AbstractTypedContentProvider  showed up in version Jetty Client 9.2 on to 10.x
    return hasClassesNamed("org.eclipse.jetty.client.util.AbstractTypedContentProvider");
  }

  public static class JettyHttpClient9Instrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.eclipse.jetty.client.HttpClient");
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
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Argument(value = 1, readOnly = false) List<Response.ResponseListener> listeners,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, httpRequest)) {
        return;
      }

      // First step is to attach the tracer to the Jetty request. Request listeners are wrapped here
      JettyHttpClient9TracingInterceptor requestInterceptor =
          new JettyHttpClient9TracingInterceptor(parentContext, instrumenter());
      requestInterceptor.attachToRequest(httpRequest);

      // Second step is to wrap all the important result callback
      listeners = wrapResponseListeners(parentContext, listeners);

      context = requestInterceptor.getContext();
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void exitTracingInterceptor(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }

      scope.close();
      if (throwable != null) {
        instrumenter().end(context, null, null, throwable);
      }
    }
  }
}
