package datadog.trace.instrumentation.rmi.context;

import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.rmi.context.ContextPayload.SETTER;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.instrumentation.api.AgentSpan;
import java.io.IOException;
import java.io.ObjectOutput;
import java.rmi.NoSuchObjectException;
import java.rmi.server.ObjID;
import lombok.extern.slf4j.Slf4j;
import sun.rmi.transport.Connection;
import sun.rmi.transport.StreamRemoteCall;
import sun.rmi.transport.TransportConstants;

@Slf4j
public class ContextPropagator {
  private static final ObjID ACTIVATOR_ID = new ObjID(ObjID.ACTIVATOR_ID);
  private static final ObjID DGC_ID = new ObjID(ObjID.DGC_ID);
  private static final ObjID REGISTRY_ID = new ObjID(ObjID.REGISTRY_ID);
  public static final ObjID DD_CONTEXT_CALL_ID = new ObjID("Datadog.v1.context_call".hashCode());
  private static final int CONTEXT_CHECK_CALL_OP_ID = -1;
  public static final int CONTEXT_PASS_OPERATION_ID = -2;

  public static boolean isRMIInternalObject(final ObjID id) {
    return ACTIVATOR_ID.equals(id) || DGC_ID.equals(id) || REGISTRY_ID.equals(id);
  }

  public static final ContextPropagator PROPAGATOR = new ContextPropagator();

  public void attemptToPropagateContext(
      final ContextStore<Connection, Boolean> contextStore,
      final Connection c,
      final AgentSpan span) {
    if (checkIfContextCanBePassed(contextStore, c)) {
      final ContextPayload payload = new ContextPayload();
      propagate().inject(span, payload, SETTER);
      if (!syntheticCall(c, payload, CONTEXT_PASS_OPERATION_ID)) {
        log.debug("Couldn't propagate context");
      }
    }
  }

  private boolean checkIfContextCanBePassed(
      final ContextStore<Connection, Boolean> contextStore, final Connection c) {
    final Boolean storedResult = contextStore.get(c);
    if (storedResult != null) {
      return storedResult;
    }

    final boolean result = syntheticCall(c, null, CONTEXT_CHECK_CALL_OP_ID);
    contextStore.put(c, result);
    return result;
  }

  private boolean syntheticCall(
      final Connection c, final ContextPayload payload, final int operationId) {
    final StreamRemoteCall shareContextCall = new StreamRemoteCall(c);
    try {
      c.getOutputStream().write(TransportConstants.Call);

      final ObjectOutput out = shareContextCall.getOutputStream();

      DD_CONTEXT_CALL_ID.write(out);

      // call header, part 2 (read by Dispatcher)
      out.writeInt(operationId); // in normal call this is method number (operation index)
      out.writeLong(operationId); // in normal RMI call this holds stub/skeleton hash

      // if method is not found by uninstrumented code then writing payload will cause an exception
      // in
      // RMI server - as the payload will be interpreted as another call
      // but it will not be parsed correctly - closing connection
      if (payload != null) {
        payload.write(out);
      }

      try {
        shareContextCall.executeCall();
      } catch (final Exception e) {
        final Exception ex = shareContextCall.getServerException();
        if (ex != null) {
          if (ex instanceof NoSuchObjectException) {
            return false;
          } else {
            log.debug("Server error when executing synthetic call", ex);
          }
        } else {
          log.debug("Error executing synthetic call", e);
        }
        return false;
      } finally {
        shareContextCall.done();
      }

    } catch (final IOException e) {
      log.debug("Communication error executing synthetic call", e);
      return false;
    }
    return true;
  }
}
