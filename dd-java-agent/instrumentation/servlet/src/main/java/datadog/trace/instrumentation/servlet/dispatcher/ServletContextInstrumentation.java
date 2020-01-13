package datadog.trace.instrumentation.servlet.dispatcher;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class ServletContextInstrumentation extends Instrumenter.Default {
  public ServletContextInstrumentation() {
    super("servlet", "servlet-dispatcher");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.servlet.ServletContext")));
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("javax.servlet.RequestDispatcher", String.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        returns(named("javax.servlet.RequestDispatcher"))
            .and(takesArgument(0, String.class))
            // javax.servlet.ServletContext.getRequestDispatcher
            // javax.servlet.ServletContext.getNamedDispatcher
            .and(isPublic()),
        RequestDispatcherTargetAdvice.class.getName());
  }

  public static class RequestDispatcherTargetAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void saveTarget(
        @Advice.Argument(0) final String target,
        @Advice.Return final RequestDispatcher dispatcher) {
      InstrumentationContext.get(RequestDispatcher.class, String.class).put(dispatcher, target);
    }
  }
}
