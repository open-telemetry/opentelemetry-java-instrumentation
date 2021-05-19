package com.example.javaagent.instrumentation;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher;
import io.opentelemetry.javaagent.extension.matcher.NameMatchers;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.util.List;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * This is a demo instrumentation which hooks into servlet invocation and modifies the http
 * response.
 */
@AutoService(InstrumentationModule.class)
public final class DemoServlet3InstrumentationModule extends InstrumentationModule {
  public DemoServlet3InstrumentationModule() {
    super("servlet-demo", "servlet-3");
  }

  /*
  We want this instrumentation to be applied after the standard servlet instrumentation.
  The latter creates a server span around http request.
  This instrumentation needs access to that server span.
   */
  @Override
  public int order() {
    return 1;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return ClassLoaderMatcher.hasClassesNamed("javax.servlet.http.HttpServlet");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DemoServlet3Instrumentation());
  }

  public static class DemoServlet3Instrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return AgentElementMatchers.safeHasSuperType(
          NameMatchers.namedOneOf("javax.servlet.Filter", "javax.servlet.http.HttpServlet"));
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
      typeTransformer.applyAdviceToMethod(
          NameMatchers.namedOneOf("doFilter", "service")
              .and(ElementMatchers
                  .takesArgument(0, ElementMatchers.named("javax.servlet.ServletRequest")))
              .and(ElementMatchers
                  .takesArgument(1, ElementMatchers.named("javax.servlet.ServletResponse")))
              .and(ElementMatchers.isPublic()),
          DemoServlet3Instrumentation.class.getName() + "$DemoServlet3Advice");
    }

    @SuppressWarnings("unused")
    public static class DemoServlet3Advice {

      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(@Advice.Argument(value = 1) ServletResponse response) {
        if (!(response instanceof HttpServletResponse)) {
          return;
        }

        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        if (!httpServletResponse.containsHeader("X-server-id")) {
          httpServletResponse.setHeader(
              "X-server-id", Java8BytecodeBridge.currentSpan().getSpanContext().getTraceId());
        }
      }
    }
  }
}
