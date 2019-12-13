package datadog.trace.instrumentation.rmi.client;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RmiClientInstrumentation extends Instrumenter.Default {

  public RmiClientInstrumentation() {
    super("rmi", "rmi-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("sun.rmi.server.UnicastRef")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ClientDecorator", "datadog.trace.agent.decorator.BaseDecorator"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("invoke"))
            .and(takesArgument(0, named("java.rmi.Remote")))
            .and(takesArgument(1, named("java.lang.reflect.Method"))),
        packageName + ".ClientAdvice");
  }
}
