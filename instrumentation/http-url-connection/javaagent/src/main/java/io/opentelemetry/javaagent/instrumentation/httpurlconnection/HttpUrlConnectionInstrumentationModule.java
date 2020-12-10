/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.javaagent.instrumentation.httpurlconnection.HttpUrlConnectionTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

@AutoService(InstrumentationModule.class)
public class HttpUrlConnectionInstrumentationModule extends InstrumentationModule {

  public HttpUrlConnectionInstrumentationModule() {
    super("http-url-connection");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpUrlConnectionInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("java.net.HttpURLConnection", Operation.class.getName());
  }

  public static class HttpUrlConnectionInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return nameStartsWith("java.net.")
          .or(ElementMatchers.<TypeDescription>nameStartsWith("sun.net"))
          // In WebLogic, URL.openConnection() returns its own internal implementation of
          // HttpURLConnection, which does not delegate the methods that have to be instrumented to
          // the JDK superclass. Therefore it needs to be instrumented directly.
          .or(named("weblogic.net.http.HttpURLConnection"))
          // This class is a simple delegator. Skip because it does not update its `connected`
          // field.
          .and(not(named("sun.net.www.protocol.https.HttpsURLConnectionImpl")))
          .and(extendsClass(named("java.net.HttpURLConnection")));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(isPublic())
              .and(namedOneOf("connect", "getOutputStream", "getInputStream")),
          HttpUrlConnectionInstrumentationModule.class.getName() + "$HttpUrlConnectionAdvice");
    }
  }

  public static class HttpUrlConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This HttpURLConnection connection,
        @Advice.FieldValue("connected") boolean connected,
        @Advice.Local("otelOperation") Operation operation,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {

      callDepth = CallDepthThreadLocalMap.getCallDepth(HttpURLConnection.class);
      if (callDepth.getAndIncrement() > 0) {
        // only want the rest of the instrumentation rules (which are complex enough) to apply to
        // top-level HttpURLConnection calls
        return;
      }

      // putting into storage for a couple of reasons:
      // - to start an operation in connect() and end it in getInputStream()
      // - to avoid creating new operation on multiple subsequent calls to getInputStream()
      ContextStore<HttpURLConnection, Operation> storage =
          InstrumentationContext.get(HttpURLConnection.class, Operation.class);
      operation = storage.get(connection);

      if (operation == null) {
        operation = tracer().startOperation(connection);
        storage.put(connection, operation);
      }

      scope = operation.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This HttpURLConnection connection,
        @Advice.FieldValue("responseCode") int responseCode,
        @Advice.Thrown Throwable throwable,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelOperation") Operation operation,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {

      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      scope.close();

      if (operation.getSpan().isRecording()) {
        if (throwable != null) {
          tracer().endExceptionally(operation, throwable);
        } else if (methodName.equals("getInputStream") && responseCode > 0) {
          // responseCode field is sometimes not populated.
          // We can't call getResponseCode() due to some unwanted side-effects
          // (e.g. breaks getOutputStream).
          tracer().end(operation, new HttpUrlResponse(connection, responseCode));
        }
      }
    }
  }
}
