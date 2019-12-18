package datadog.trace.instrumentation.rmi.context;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class RmiContextInstrumentation extends Instrumenter.Default {

  public RmiContextInstrumentation() {
    super("rmi", "rmi-context-propagator");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(
            safeHasSuperType(
                named("sun.rmi.transport.StreamRemoteCall")
                    .or(named("sun.rmi.transport.ObjectTable"))
                    .or(named("sun.rmi.transport.ObjectEndpoint"))));
  }

  @Override
  public Map<String, String> contextStore() {
    final HashMap<String, String> contextStore = new HashMap<>();
    // thread context that stores distributed context
    contextStore.put("java.lang.Thread", "datadog.trace.instrumentation.api.AgentSpan$Context");

    // caching if a connection can support enhanced format
    contextStore.put("sun.rmi.transport.Connection", "java.lang.Boolean");
    return contextStore;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ContextPayload",
      packageName + ".ContextPayload$InjectAdapter",
      packageName + ".ContextPayload$ExtractAdapter",
      packageName + ".ContextDispatcher",
      packageName + ".StreamRemoteCallConstructorAdvice",
      packageName + ".ObjectTableAdvice",
      packageName + ".ObjectTableAdvice$DummyRemote"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isConstructor()
            .and(takesArgument(0, named("sun.rmi.transport.Connection")))
            .and(takesArgument(1, named("java.rmi.server.ObjID"))),
        packageName + ".StreamRemoteCallConstructorAdvice");

    transformers.put(
        isMethod()
            .and(isStatic())
            .and(named("getTarget"))
            .and((takesArgument(0, named("sun.rmi.transport.ObjectEndpoint")))),
        packageName + ".ObjectTableAdvice");

    return Collections.unmodifiableMap(transformers);
  }
}
