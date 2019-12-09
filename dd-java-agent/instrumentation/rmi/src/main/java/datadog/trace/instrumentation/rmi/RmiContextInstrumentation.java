package datadog.trace.instrumentation.rmi;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.rmi.context.ContextPayload.GETTER;
import static datadog.trace.instrumentation.rmi.context.StreamRemoteCallConstructorAdvice.DD_CONTEXT_CALL_ID;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.rmi.context.ContextPayload;
import datadog.trace.instrumentation.rmi.context.StreamRemoteCallConstructorAdvice;
import java.io.IOException;
import java.io.ObjectInput;
import java.lang.reflect.Field;
import java.rmi.Remote;
import java.rmi.server.ObjID;
import java.rmi.server.RemoteCall;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import sun.rmi.server.Dispatcher;
import sun.rmi.transport.ObjectTable;
import sun.rmi.transport.StreamRemoteCall;
import sun.rmi.transport.Target;

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
                named(StreamRemoteCall.class.getName()).or(named(ObjectTable.class.getName()))));
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(Thread.class.getName(), AgentSpan.Context.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.rmi.context.StreamRemoteCallConstructorAdvice",
      "datadog.trace.instrumentation.rmi.context.ContextPayload",
      "datadog.trace.instrumentation.rmi.context.ContextPayload$InjectAdapter",
      "datadog.trace.instrumentation.rmi.context.ContextPayload$ExtractAdapter",
      "datadog.trace.instrumentation.rmi.RmiContextInstrumentation$ObjectTableAdvice",
      "datadog.trace.instrumentation.rmi.RmiContextInstrumentation$ContextDispatcher",
      "datadog.trace.instrumentation.rmi.RmiContextInstrumentation$ObjectTableAdvice$DummyRemote"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    // TODO make this more specific
    transformers.put(
        isConstructor()
            .and(takesArgument(0, named("sun.rmi.transport.Connection")))
            .and(takesArgument(1, named("java.rmi.server.ObjID"))),
        "datadog.trace.instrumentation.rmi.context.StreamRemoteCallConstructorAdvice");

    transformers.put(
        isMethod().and(isStatic()).and(named("getTarget")),
        getClass().getName() + "$ObjectTableAdvice");
    return transformers;
  }

  public static class ObjectTableAdvice {
    public static final DummyRemote DUMMY_REMOTE = new DummyRemote();

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) final Object oe, @Advice.Return(readOnly = false) Target result) {
      final ObjID objID = GET_OBJ_ID(oe);
      if (!DD_CONTEXT_CALL_ID.equals(objID)) {
        return;
      }
      final ContextStore<Thread, AgentSpan.Context> callableContextStore =
          InstrumentationContext.get(Thread.class, AgentSpan.Context.class);

      result =
          new Target(
              DUMMY_REMOTE,
              new ContextDispatcher(callableContextStore),
              DUMMY_REMOTE,
              objID,
              false);
    }

    public static ObjID GET_OBJ_ID(final Object oe) {
      try {
        final Class<?> clazz = oe.getClass();
        // sun.rmi.transport.ObjectEndpoint is protected and field "id" is private
        final Field id = clazz.getDeclaredField("id");
        id.setAccessible(true);
        return (ObjID) id.get(oe);
      } catch (final ReflectiveOperationException e) {
        // TODO: log it
      }
      return null;
    }

    public static class DummyRemote implements Remote {}
  }

  public static class ContextDispatcher implements Dispatcher {

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
        try {
          final Object payload = in.readObject();
          if (payload instanceof ContextPayload) {
            final AgentSpan.Context context = propagate().extract((ContextPayload) payload, GETTER);
            callableContextStore.put(Thread.currentThread(), context);
          }
        } catch (final ClassNotFoundException e) {
          // TODO log e.printStackTrace();
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
}
