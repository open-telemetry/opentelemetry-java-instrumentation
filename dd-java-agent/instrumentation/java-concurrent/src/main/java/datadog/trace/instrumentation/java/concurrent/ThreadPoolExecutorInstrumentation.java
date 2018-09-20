package datadog.trace.instrumentation.java.concurrent;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public class ThreadPoolExecutorInstrumentation extends Instrumenter.Default {

  public ThreadPoolExecutorInstrumentation() {
    super(ExecutorInstrumentation.EXEC_NAME);
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("java.util.concurrent.ThreadPoolExecutor");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      ExecutorInstrumentation.class.getName() + "$ConcurrentUtils",
      ThreadPoolExecutorInstrumentation.class.getName() + "$GenericRunnable",
    };
  }

  @Override
  public Map<? extends ElementMatcher, String> transformers() {
    return Collections.singletonMap(
        isConstructor()
            .and(takesArgument(4, named("java.util.concurrent.BlockingQueue")))
            .and(takesArguments(7)),
        ThreadPoolExecutorAdvice.class.getName());
  }

  public static class ThreadPoolExecutorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void disableIfQueueWrongType(
        @Advice.This final ThreadPoolExecutor executor,
        @Advice.Argument(4) final BlockingQueue queue) {

      if (queue.size() == 0) {
        try {
          queue.add(new GenericRunnable());
          System.err.println("ADDED to queue " + queue.getClass());
          queue.clear(); // Remove the Runnable we just added.
        } catch (final ClassCastException e) {
          ExecutorInstrumentation.ConcurrentUtils.disableExecutor(executor);
        }
      }
    }
  }

  public static class GenericRunnable implements Runnable {

    @Override
    public void run() {}
  }
}
