/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.tomcat.v10_0.Tomcat10Singletons.helper;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatServerHandlerInstrumentation;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

@AutoService(InstrumentationModule.class)
public class Tomcat10InstrumentationModule extends InstrumentationModule {

  public Tomcat10InstrumentationModule() {
    super("tomcat", "tomcat-10.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
            // added in Servlet 5.0 (renamed from javax.servlet)
            "jakarta.servlet.http.HttpServletRequest",
            // Tomcat request class
            "org.apache.coyote.Request")
        // removed in 10.0
        .and(not(hasClassesNamed("org.apache.catalina.loader.Constants")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(
        new TomcatServerHandlerInstrumentation(
            getClass().getName() + "$Tomcat10ServerHandlerAdvice",
            getClass().getName() + "$Tomcat10AttachResponseAdvice"));
  }

  @SuppressWarnings("unused")
  public static class Tomcat10AttachResponseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void attachResponse(
        @Advice.Argument(2) Response response, @Advice.Return boolean success) {

      if (success) {
        helper().attachResponseToRequest(Java8BytecodeBridge.currentContext(), response);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class Tomcat10ServerHandlerAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      private AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Request request, Response response) {
        Context parentContext = Context.current();
        if (!helper().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = helper().start(parentContext, request);

        Scope scope = context.makeCurrent();

        HttpServerResponseCustomizerHolder.getCustomizer()
            .customize(context, response, Tomcat10ResponseMutator.INSTANCE);

        return new AdviceScope(context, scope);
      }

      public void end(Request request, Response response, @Nullable Throwable throwable) {
        helper().end(request, response, throwable, context, scope);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.Argument(0) Request request, @Advice.Argument(1) Response response) {
      return AdviceScope.start(request, response);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) Response response,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(request, response, throwable);
      }
    }
  }
}
