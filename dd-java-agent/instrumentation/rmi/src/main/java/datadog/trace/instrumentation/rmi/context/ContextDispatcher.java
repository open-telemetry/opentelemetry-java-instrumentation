package datadog.trace.instrumentation.rmi.context;

import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.rmi.context.ContextPayload.GETTER;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.instrumentation.api.AgentSpan;
import java.io.IOException;
import java.io.ObjectInput;
import java.rmi.Remote;
import java.rmi.server.RemoteCall;
import sun.rmi.server.Dispatcher;

public class ContextDispatcher implements Dispatcher {

  private final ContextStore<Thread, AgentSpan.Context> callableContextStore;

  public ContextDispatcher(final ContextStore<Thread, AgentSpan.Context> callableContextStore) {
    this.callableContextStore = callableContextStore;
  }

  @Override
  public void dispatch(final Remote obj, final RemoteCall call) throws IOException {
    final ObjectInput in = call.getInputStream();
    final int operationId = in.readInt();
    in.readLong(); // skip 8 bytes

    if (operationId == StreamRemoteCallConstructorAdvice.CONTEXT_PASS_OPERATION_ID) {
      final ContextPayload payload = ContextPayload.read(in);
      if (payload != null) {
        final AgentSpan.Context context = propagate().extract(payload, GETTER);

        callableContextStore.put(Thread.currentThread(), context);
      }
    }

    // send result stream the client is expecting
    call.getResultStream(true);

    // release held streams to allow next call to continue
    call.releaseInputStream();
    call.releaseOutputStream();
    call.done();
  }
}
