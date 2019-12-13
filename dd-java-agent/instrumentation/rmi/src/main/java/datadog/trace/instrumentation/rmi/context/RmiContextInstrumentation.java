package datadog.trace.instrumentation.rmi.context;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.api.AgentSpan;
import java.rmi.server.ObjID;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import sun.rmi.transport.Connection;
import sun.rmi.transport.ObjectTable;
import sun.rmi.transport.StreamRemoteCall;

@AutoService(Instrumenter.class)
public class RmiContextInstrumentation extends Instrumenter.Default {
  // TODO clean this up

  public RmiContextInstrumentation() {
    super("rmi", "rmi-context-propagator");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(
            safeHasSuperType(
                named(StreamRemoteCall.class.getName()) // TODO replace with string
                    .or(named(ObjectTable.class.getName()))
                    .or(named("sun.rmi.transport.ObjectEndpoint"))));
  }

  @Override
  public Map<String, String> contextStore() {
    final HashMap<String, String> contextStore = new HashMap<>();
    // thread context that stores distributed context
    contextStore.put(Thread.class.getName(), AgentSpan.Context.class.getName());

    // caching if a connection can support enhanced format
    contextStore.put(Connection.class.getName(), Boolean.class.getName());

    // used to avoid reflection when instrumenting protected class ObjectEndpoint
    contextStore.put(Object.class.getName(), ObjID.class.getName());
    return contextStore;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".StreamRemoteCallConstructorAdvice",
      packageName + ".ContextPayload",
      packageName + ".ContextPayload$InjectAdapter",
      packageName + ".ContextPayload$ExtractAdapter",
      packageName + ".ObjectTableAdvice",
      packageName + ".ContextDispatcher",
      packageName + ".ObjectEndpointConstructorAdvice",
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

    transformers.put(
        isConstructor().and(isDeclaredBy(named("sun.rmi.transport.ObjectEndpoint"))),
        packageName + ".ObjectEndpointConstructorAdvice");
    return transformers;
  }
}
