/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.rmi.context.server;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator.extract;
import static io.opentelemetry.auto.bootstrap.instrumentation.rmi.ThreadLocalContext.THREAD_LOCAL_CONTEXT;
import static io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload.GETTER;
import static io.opentelemetry.auto.instrumentation.rmi.context.ContextPropagator.CONTEXT_CALL_ID;
import static io.opentelemetry.auto.instrumentation.rmi.context.ContextPropagator.PROPAGATOR;

import io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload;
import io.opentelemetry.trace.SpanContext;
import java.io.IOException;
import java.io.ObjectInput;
import java.rmi.Remote;
import java.rmi.server.RemoteCall;
import sun.rmi.server.Dispatcher;
import sun.rmi.transport.Target;

/**
 * ContextDispatcher is responsible for handling both initial context propagation check call and
 * following call which carries payload
 *
 * <p>Context propagation check is only expected not to throw any exception, hinting to the client
 * that its communicating with an instrumented server. Non instrumented server would've thrown
 * UnknownObjectException
 *
 * <p>Because caching of the result after first call on a connection, only payload calls are
 * expected
 */
public class ContextDispatcher implements Dispatcher {
  private static final ContextDispatcher CONTEXT_DISPATCHER = new ContextDispatcher();
  private static final NoopRemote NOOP_REMOTE = new NoopRemote();

  public static Target newDispatcherTarget() {
    return new Target(NOOP_REMOTE, CONTEXT_DISPATCHER, NOOP_REMOTE, CONTEXT_CALL_ID, false);
  }

  @Override
  public void dispatch(final Remote obj, final RemoteCall call) throws IOException {
    final ObjectInput in = call.getInputStream();
    final int operationId = in.readInt();
    in.readLong(); // skip 8 bytes

    if (PROPAGATOR.isOperationWithPayload(operationId)) {
      final ContextPayload payload = ContextPayload.read(in);
      if (payload != null) {
        final SpanContext context = extract(payload, GETTER);
        if (context.isValid()) {
          THREAD_LOCAL_CONTEXT.set(context);
        } else {
          THREAD_LOCAL_CONTEXT.set(null);
        }
      }
    }

    // send result stream the client is expecting
    call.getResultStream(true);

    // release held streams to allow next call to continue
    call.releaseInputStream();
    call.releaseOutputStream();
    call.done();
  }

  public static class NoopRemote implements Remote {}
}
