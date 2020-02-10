package datadog.trace.instrumentation.rmi.context.client;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.rmi.context.ContextPropagator.PROPAGATOR;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.rmi.server.ObjID;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import sun.rmi.transport.Connection;

/**
 * Main entry point for transferring context between RMI service.
 *
 * <p>It injects into StreamRemoteCall constructor used for invoking remote tasks and performs a
 * backwards compatible check to ensure if the other side is prepared to receive context propagation
 * messages then if successful sends a context propagation message
 *
 * <p>Context propagation consist of a Serialized HashMap with all data set by usual context
 * injection, which includes things like sampling priority, trace and parent id
 *
 * <p>As well as optional baggage items
 *
 * <p>On the other side of the communication a special Dispatcher is created when a message with
 * DD_CONTEXT_CALL_ID is received.
 *
 * <p>If the server is not instrumented first call will gracefully fail just like any other unknown
 * call. With small caveat that this first call needs to *not* have any parameters, since those will
 * not be read from connection and instead will be interpreted as another remote instruction, but
 * that instruction will essentially be garbage data and will cause the parsing loop to throw
 * exception and shutdown the connection which we do not want
 */
@AutoService(Instrumenter.class)
public class RmiClientContextInstrumentation extends Instrumenter.Default {

  public RmiClientContextInstrumentation() {
    super("rmi", "rmi-context-propagator", "rmi-client-context-propagator");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("sun.rmi.transport.StreamRemoteCall")));
  }

  @Override
  public Map<String, String> contextStore() {
    // caching if a connection can support enhanced format
    return singletonMap("sun.rmi.transport.Connection", "java.lang.Boolean");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.rmi.context.ContextPayload$InjectAdapter",
      "datadog.trace.instrumentation.rmi.context.ContextPayload$ExtractAdapter",
      "datadog.trace.instrumentation.rmi.context.ContextPayload",
      "datadog.trace.instrumentation.rmi.context.ContextPropagator"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor()
            .and(takesArgument(0, named("sun.rmi.transport.Connection")))
            .and(takesArgument(1, named("java.rmi.server.ObjID"))),
        getClass().getName() + "$StreamRemoteCallConstructorAdvice");
  }

  public static class StreamRemoteCallConstructorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0) final Connection c,
        @Advice.Argument(value = 1) final ObjID id) {
      if (!c.isReusable()) {
        return;
      }
      if (PROPAGATOR.isRMIInternalObject(id)) {
        return;
      }
      final AgentSpan activeSpan = activeSpan();
      if (activeSpan == null) {
        return;
      }

      final ContextStore<Connection, Boolean> knownConnections =
          InstrumentationContext.get(Connection.class, Boolean.class);

      PROPAGATOR.attemptToPropagateContext(knownConnections, c, activeSpan);
    }
  }
}
