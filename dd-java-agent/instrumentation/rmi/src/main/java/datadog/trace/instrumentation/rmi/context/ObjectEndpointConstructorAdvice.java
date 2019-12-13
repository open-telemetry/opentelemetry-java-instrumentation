package datadog.trace.instrumentation.rmi.context;

import datadog.trace.bootstrap.InstrumentationContext;
import java.rmi.server.ObjID;
import net.bytebuddy.asm.Advice;

public class ObjectEndpointConstructorAdvice {
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This final Object thiz, @Advice.Argument(value = 0) final ObjID id) {
    if (id != null) {
      InstrumentationContext.get(Object.class, ObjID.class).put(thiz, id);
    }
  }
}
