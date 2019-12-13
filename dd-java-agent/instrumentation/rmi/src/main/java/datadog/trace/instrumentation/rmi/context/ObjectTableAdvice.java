package datadog.trace.instrumentation.rmi.context;

import static datadog.trace.instrumentation.rmi.context.StreamRemoteCallConstructorAdvice.DD_CONTEXT_CALL_ID;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.api.AgentSpan;
import java.lang.reflect.Field;
import java.rmi.Remote;
import java.rmi.server.ObjID;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import sun.rmi.transport.Target;

@Slf4j
public class ObjectTableAdvice {
  public static final DummyRemote DUMMY_REMOTE = new DummyRemote();

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void methodExit(
      @Advice.Argument(0) final Object oe, @Advice.Return(readOnly = false) Target result) {
    final ObjID objID = InstrumentationContext.get(Object.class, ObjID.class).get(oe);

    if (!DD_CONTEXT_CALL_ID.equals(objID)) {
      return;
    }

    final ContextStore<Thread, AgentSpan.Context> callableContextStore =
        InstrumentationContext.get(Thread.class, AgentSpan.Context.class);

    result =
        new Target(
            DUMMY_REMOTE, new ContextDispatcher(callableContextStore), DUMMY_REMOTE, objID, false);
  }

  public static ObjID GET_OBJ_ID(final Object oe) {
    try {
      final Class<?> clazz = oe.getClass();
      // sun.rmi.transport.ObjectEndpoint is protected and field "id" is private
      final Field id = clazz.getDeclaredField("id");
      id.setAccessible(true);
      return (ObjID) id.get(oe);
    } catch (final ReflectiveOperationException e) {
      log.debug("Error getting object id from: {}", oe, e);
    }
    return null;
  }

  public static class DummyRemote implements Remote {}
}
