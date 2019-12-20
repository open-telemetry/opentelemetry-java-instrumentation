package datadog.trace.instrumentation.rmi.context.server;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
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
public class RmiServerContextInstrumentation extends Instrumenter.Default {

  public RmiServerContextInstrumentation() {
    super("rmi", "rmi-context-propagator", "rmi-server-context-propagator");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("sun.rmi.transport.ObjectTable")));
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("java.lang.Thread", "datadog.trace.instrumentation.api.AgentSpan$Context");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.rmi.context.ContextPayload$InjectAdapter",
      "datadog.trace.instrumentation.rmi.context.ContextPayload$ExtractAdapter",
      "datadog.trace.instrumentation.rmi.context.ContextPayload",
      "datadog.trace.instrumentation.rmi.context.ContextPropagator",
      packageName + ".ContextDispatcher",
      packageName + ".ObjectTableAdvice$NoopRemote"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isStatic())
            .and(named("getTarget"))
            .and((takesArgument(0, named("sun.rmi.transport.ObjectEndpoint")))),
        packageName + ".ObjectTableAdvice");
  }
}
