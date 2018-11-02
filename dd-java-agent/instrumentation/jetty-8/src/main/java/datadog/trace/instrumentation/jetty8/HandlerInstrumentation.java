package datadog.trace.instrumentation.jetty8;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class HandlerInstrumentation extends Instrumenter.Default {

  public HandlerInstrumentation() {
    super("jetty", "jetty-8");
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("org.eclipse.jetty.server.Handler")))
        .and(not(named("org.eclipse.jetty.server.handler.HandlerWrapper")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.jetty8.HttpServletRequestExtractAdapter",
      "datadog.trace.instrumentation.jetty8.HttpServletRequestExtractAdapter$MultivaluedMapFlatIterator",
      "datadog.trace.instrumentation.jetty8.TagSettingAsyncListener"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        named("handle")
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("org.eclipse.jetty.server.Request")))
            .and(takesArgument(2, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(3, named("javax.servlet.http.HttpServletResponse")))
            .and(isPublic()),
        JettyHandlerAdvice.class.getName());
    return transformers;
  }
}
