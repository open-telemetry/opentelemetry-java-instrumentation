/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1;

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
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JaxRsClientInstrumentationModule extends InstrumentationModule {

  public JaxRsClientInstrumentationModule() {
    super("jaxrs-client", "jaxrs-client-1.1");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ClientHandlerInstrumentation());
  }

  public static class ClientHandlerInstrumentation implements TypeInstrumentation {
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
        @Advice.Local("otelOperation") Operation<ClientResponse> operation,
        @Advice.Local("otelScope") Scope scope) {
      operation = tracer().startOperation(request);
      scope = operation.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Return ClientResponse response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelOperation") Operation<ClientResponse> operation,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      operation.endMaybeExceptionally(response, throwable);
    }
  }
}
