/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1;

import static io.opentelemetry.instrumentation.api.tracer.HttpServerTracer.CONTEXT_ATTRIBUTE;
import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1.JaxRsClientV1Tracer.tracer;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class JaxRsClientInstrumentationModule extends InstrumentationModule {

  public JaxRsClientInstrumentationModule() {
    super("jaxrs-client", "jaxrs-client-1.1");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JaxRsClientV1Tracer", packageName + ".InjectAdapter",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ClientHandlerInstrumentation());
  }

  private static final class ClientHandlerInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("com.sun.jersey.api.client.ClientHandler");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(named("com.sun.jersey.api.client.ClientHandler"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          named("handle")
              .and(takesArgument(0, extendsClass(named("com.sun.jersey.api.client.ClientRequest"))))
              .and(returns(extendsClass(named("com.sun.jersey.api.client.ClientResponse")))),
          JaxRsClientInstrumentationModule.class.getName() + "$HandleAdvice");
    }
  }

  public static class HandleAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
        @Advice.Argument(0) ClientRequest request,
        @Advice.This ClientHandler thisObj,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {

      // WARNING: this might be a chain...so we only have to trace the first in the chain.
      boolean isRootClientHandler = null == request.getProperties().get(CONTEXT_ATTRIBUTE);
      if (isRootClientHandler) {
        span = tracer().startSpan(request);
        scope = tracer().startScope(span, request);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Return ClientResponse response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }

      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
      } else {
        tracer().end(span, response);
      }
    }
  }
}
