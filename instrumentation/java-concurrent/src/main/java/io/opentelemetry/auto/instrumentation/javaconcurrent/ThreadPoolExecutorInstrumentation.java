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

package io.opentelemetry.auto.instrumentation.javaconcurrent;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.GenericRunnable;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
/**
 * Disable instrumentation for executors that cannot take our runnable wrappers.
 *
 * <p>FIXME: We should remove this once https://github.com/raphw/byte-buddy/issues/558 is fixed
 */
public class ThreadPoolExecutorInstrumentation extends Instrumenter.Default {

  public ThreadPoolExecutorInstrumentation() {
    super(AbstractExecutorInstrumentation.EXEC_NAME);
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("java.util.concurrent.ThreadPoolExecutor");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor()
            .and(takesArgument(4, named("java.util.concurrent.BlockingQueue")))
            .and(takesArguments(7)),
        ThreadPoolExecutorInstrumentation.class.getName() + "$ThreadPoolExecutorAdvice");
  }

  public static class ThreadPoolExecutorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void disableIfQueueWrongType(
        @Advice.This final ThreadPoolExecutor executor,
        @Advice.Argument(4) final BlockingQueue<Runnable> queue) {

      if (queue.isEmpty()) {
        try {
          queue.offer(new GenericRunnable());
          queue.clear(); // Remove the Runnable we just added.
        } catch (final ClassCastException | IllegalArgumentException e) {
          // These errors indicate the queue is fundamentally incompatible with wrapped runnables.
          // We must disable the executor instance to avoid passing wrapped runnables later.
          ExecutorInstrumentationUtils.disableExecutorForWrappedTasks(executor);
        } catch (final Exception e) {
          // Other errors might indicate the queue is not fully initialized.
          // We might want to disable for those too, but for now just ignore.
        }
      }
    }
  }
}
