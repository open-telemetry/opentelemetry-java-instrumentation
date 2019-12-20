package datadog.trace.instrumentation.rmi.context.client;

import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.rmi.context.ContextPropagator.PROPAGATOR;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.rmi.context.ContextPropagator;
import java.rmi.server.ObjID;
import net.bytebuddy.asm.Advice;
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
public class StreamRemoteCallConstructorAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(value = 0) final Connection c, @Advice.Argument(value = 1) final ObjID id) {
    if (!c.isReusable()) {
      return;
    }
    if (ContextPropagator.isRMIInternalObject(id)) {
      return;
    }
    final AgentSpan activeSpan = activeSpan();
    if (activeSpan == null) {
      return;
    }

    final ContextStore<Connection, Boolean> contextStore =
        InstrumentationContext.get(Connection.class, Boolean.class);

    PROPAGATOR.attemptToPropagateContext(contextStore, c, activeSpan);
  }
}
