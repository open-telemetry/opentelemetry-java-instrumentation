/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.httpurlconnection.HttpUrlConnectionTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpStatusConverter;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.HttpURLConnection;
import java.util.HashMap;
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
      Map<ElementMatcher<MethodDescription>, String> map = new HashMap<>();
      map.put(
          isMethod()
              .and(isPublic())
              .and(namedOneOf("connect", "getOutputStream", "getInputStream")),
          HttpUrlConnectionInstrumentationModule.class.getName() + "$HttpUrlConnectionAdvice");
      map.put(
          isMethod().and(isPublic()).and(named("getResponseCode")),
          HttpUrlConnectionInstrumentationModule.class.getName() + "$GetResponseCodeAdvice");
      return map;
    }
  }

  public static class HttpUrlConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This HttpURLConnection connection,
        @Advice.FieldValue("connected") boolean connected,
        @Advice.Local("otelHttpUrlState") HttpUrlState httpUrlState,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {

      callDepth = CallDepthThreadLocalMap.getCallDepth(HttpURLConnection.class);
      if (callDepth.getAndIncrement() > 0) {
        // only want the rest of the instrumentation rules (which are complex enough) to apply to
        // top-level HttpURLConnection calls
        return;
      }
      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      // using storage for a couple of reasons:
      // - to start an operation in connect() and end it in getInputStream()
      // - to avoid creating a new operation on multiple subsequent calls to getInputStream()
      ContextStore<HttpURLConnection, HttpUrlState> storage =
          InstrumentationContext.get(HttpURLConnection.class, HttpUrlState.class);
      httpUrlState = storage.get(connection);

      if (httpUrlState != null) {
        if (!httpUrlState.finished) {
          scope = httpUrlState.context.makeCurrent();
        }
        return;
      }

      Context context = tracer().startSpan(parentContext, connection);
      httpUrlState = new HttpUrlState(context);
      storage.put(connection, httpUrlState);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This HttpURLConnection connection,
        @Advice.FieldValue("responseCode") int responseCode,
        @Advice.Thrown Throwable throwable,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelHttpUrlState") HttpUrlState httpUrlState,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable != null) {
        tracer().endExceptionally(httpUrlState.context, throwable);
        httpUrlState.finished = true;
      } else if (methodName.equals("getInputStream") && responseCode > 0) {
        // responseCode field is sometimes not populated.
        // We can't call getResponseCode() due to some unwanted side-effects
        // (e.g. breaks getOutputStream).
        tracer().end(httpUrlState.context, new HttpUrlResponse(connection, responseCode));
        httpUrlState.finished = true;
      }
    }
  }

  public static class GetResponseCodeAdvice {

    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This HttpURLConnection connection, @Advice.Return int returnValue) {

      ContextStore<HttpURLConnection, HttpUrlState> storage =
          InstrumentationContext.get(HttpURLConnection.class, HttpUrlState.class);
      HttpUrlState httpUrlState = storage.get(connection);
      if (httpUrlState != null) {
        Span span = Java8BytecodeBridge.spanFromContext(httpUrlState.context);
        span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, returnValue);
        span.setStatus(HttpStatusConverter.statusFromHttpStatus(returnValue));
      }
    }
  }

  // everything is public since called directly from advice code
  // (which is inlined into other packages)
  public static class HttpUrlState {
    public final Context context;
    public boolean finished;

    public HttpUrlState(Context context) {
      this.context = context;
    }
  }
}
