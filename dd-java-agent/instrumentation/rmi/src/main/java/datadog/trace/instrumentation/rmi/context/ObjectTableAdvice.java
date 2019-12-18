package datadog.trace.instrumentation.rmi.context;

import static datadog.trace.instrumentation.rmi.context.StreamRemoteCallConstructorAdvice.DD_CONTEXT_CALL_ID;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.api.AgentSpan;
import java.rmi.Remote;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import sun.rmi.transport.Target;

@Slf4j
public class ObjectTableAdvice {
  public static final DummyRemote DUMMY_REMOTE = new DummyRemote();

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void methodExit(
      @Advice.Argument(0) final Object oe, @Advice.Return(readOnly = false) Target result) {

    // comparing toString() output allows us to avoid using reflection to be able to compare
    // ObjID and ObjectEndpoint objects
    // ObjectEndpoint#toString() only returns this.objId.toString() value which is exactly
    // what we're interested in here.
    if (!DD_CONTEXT_CALL_ID.toString().equals(oe.toString())) {
      return;
    }

    final ContextStore<Thread, AgentSpan.Context> callableContextStore =
        InstrumentationContext.get(Thread.class, AgentSpan.Context.class);

    result =
        new Target(
            DUMMY_REMOTE,
            new ContextDispatcher(callableContextStore),
            DUMMY_REMOTE,
            DD_CONTEXT_CALL_ID,
            false);
  }

  public static class DummyRemote implements Remote {}
}
