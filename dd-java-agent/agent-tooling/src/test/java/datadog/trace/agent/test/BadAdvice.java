package datadog.trace.agent.test;

import net.bytebuddy.asm.Advice;

public class BadAdvice {
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void throwAnException(@Advice.Return(readOnly = false) boolean returnVal) {
    returnVal = true;
    throw new RuntimeException("Test Exception");
  }

  public static class NoOpAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void doNothing() {
      System.currentTimeMillis();
    }
  }
}
