package com.example.javaagent.instrumentation;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.NameMatchers;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
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
  public int getOrder() {
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
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          NameMatchers.namedOneOf("doFilter", "service")
              .and(ElementMatchers.takesArgument(0, ElementMatchers.named("javax.servlet.ServletRequest")))
              .and(ElementMatchers.takesArgument(1, ElementMatchers.named("javax.servlet.ServletResponse")))
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
