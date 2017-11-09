package dd.trace;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;

public class ExceptionHandlers {

  private static final StackManipulation STACK_TRACE_HANDLER;

  static {
    StackManipulation handler;
    try {
      handler =
          MethodInvocation.invoke(
              new MethodDescription.ForLoadedMethod(Throwable.class.getMethod("printStackTrace")));
    } catch (final NoSuchMethodException e) {
      handler = StackManipulation.Trivial.INSTANCE;
    }
    STACK_TRACE_HANDLER = handler;
  }

  public static StackManipulation defaultExceptionHandler() {
    return STACK_TRACE_HANDLER;
  }
}
