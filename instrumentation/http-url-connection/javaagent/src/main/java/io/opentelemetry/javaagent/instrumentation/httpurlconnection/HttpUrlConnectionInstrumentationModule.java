/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
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
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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
    super("httpurlconnection");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpUrlConnectionInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("java.net.HttpURLConnection", getClass().getName() + "$HttpUrlState");
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
    public static HttpUrlState methodEnter(
        @Advice.This HttpURLConnection connection,
        @Advice.FieldValue("connected") boolean connected,
        @Advice.Local("otelScope") Scope scope) {

      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpURLConnection.class);
      if (callDepth > 0) {
        return null;
      }

      ContextStore<HttpURLConnection, HttpUrlState> contextStore =
          InstrumentationContext.get(HttpURLConnection.class, HttpUrlState.class);
      HttpUrlState state = contextStore.putIfAbsent(connection, HttpUrlState::new);

      synchronized (state) {
        if (!state.initialized) {
          Context parentContext = currentContext();
          if (tracer().shouldStartSpan(parentContext)) {
            state.context = tracer().startOperation(parentContext, connection, connection);
            if (!connected) {
              scope = state.context.makeCurrent();
            }
          }
          state.initialized = true;
        }
      }
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter HttpUrlState state,
        @Advice.This HttpURLConnection connection,
        @Advice.FieldValue("responseCode") int responseCode,
        @Advice.Thrown Throwable throwable,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelScope") Scope scope) {

      if (scope != null) {
        scope.close();
      }
      if (state == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(HttpURLConnection.class);

      synchronized (state) {
        if (state.context != null && !state.finished) {
          if (throwable != null) {
            tracer().endExceptionally(state.context, throwable);
            state.finished = true;
          } else if ("getInputStream".equals(methodName)) {
            // responseCode field is sometimes not populated.
            // We can't call getResponseCode() due to some unwanted side-effects
            // (e.g. breaks getOutputStream).
            if (responseCode > 0) {
              tracer().end(state.context, new HttpUrlResponse(connection, responseCode));
              state.finished = true;
            }
          }
        }
      }
    }
  }

  // state is always accessed under synchronized block
  public static class HttpUrlState {
    public boolean initialized;
    public Context context;
    public boolean finished;
  }
}
