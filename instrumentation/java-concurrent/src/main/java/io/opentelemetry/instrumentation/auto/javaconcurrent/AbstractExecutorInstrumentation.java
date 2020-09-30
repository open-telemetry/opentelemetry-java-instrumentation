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

package io.opentelemetry.instrumentation.auto.javaconcurrent;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractExecutorInstrumentation extends Instrumenter.Default {

  private static final Logger log = LoggerFactory.getLogger(AbstractExecutorInstrumentation.class);

  public static final String EXEC_NAME = "java_concurrent";

  private static final String TRACE_EXECUTORS_CONFIG = "otel.trace.executors";
  private final boolean TRACE_ALL_EXECUTORS =
      Config.get().getBooleanProperty("otel.trace.executors.all", false);

  /**
   * Only apply executor instrumentation to allowed executors. To apply to all executors, use
   * override setting above.
   */
  private final Collection<String> ALLOWED_EXECUTORS;

  /**
   * Some frameworks have their executors defined as anon classes inside other classes. Referencing
   * anon classes by name would be fragile, so instead we will use list of class prefix names. Since
   * checking this list is more expensive (O(n)) we should try to keep it short.
   */
  private final Collection<String> ALLOWED_EXECUTORS_PREFIXES;

  public AbstractExecutorInstrumentation(String... additionalNames) {
    super(EXEC_NAME, additionalNames);

    if (TRACE_ALL_EXECUTORS) {
      log.info("Tracing all executors enabled.");
      ALLOWED_EXECUTORS = Collections.emptyList();
      ALLOWED_EXECUTORS_PREFIXES = Collections.emptyList();
    } else {
      String[] allowed = {
        "akka.actor.ActorSystemImpl$$anon$1",
        "akka.dispatch.BalancingDispatcher",
        "akka.dispatch.Dispatcher",
        "akka.dispatch.Dispatcher$LazyExecutorServiceDelegate",
        "akka.dispatch.ExecutionContexts$sameThreadExecutionContext$",
        "akka.dispatch.forkjoin.ForkJoinPool",
        "akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinPool",
        "akka.dispatch.MessageDispatcher",
        "akka.dispatch.PinnedDispatcher",
        "com.google.common.util.concurrent.AbstractListeningExecutorService",
        "com.google.common.util.concurrent.MoreExecutors$ListeningDecorator",
        "com.google.common.util.concurrent.MoreExecutors$ScheduledListeningDecorator",
        "io.netty.channel.epoll.EpollEventLoop",
        "io.netty.channel.epoll.EpollEventLoopGroup",
        "io.netty.channel.MultithreadEventLoopGroup",
        "io.netty.channel.nio.NioEventLoop",
        "io.netty.channel.nio.NioEventLoopGroup",
        "io.netty.channel.SingleThreadEventLoop",
        "io.netty.util.concurrent.AbstractEventExecutor",
        "io.netty.util.concurrent.AbstractEventExecutorGroup",
        "io.netty.util.concurrent.AbstractScheduledEventExecutor",
        "io.netty.util.concurrent.DefaultEventExecutor",
        "io.netty.util.concurrent.DefaultEventExecutorGroup",
        "io.netty.util.concurrent.GlobalEventExecutor",
        "io.netty.util.concurrent.MultithreadEventExecutorGroup",
        "io.netty.util.concurrent.SingleThreadEventExecutor",
        "java.util.concurrent.AbstractExecutorService",
        "java.util.concurrent.CompletableFuture$ThreadPerTaskExecutor",
        "java.util.concurrent.Executors$DelegatedExecutorService",
        "java.util.concurrent.Executors$FinalizableDelegatedExecutorService",
        "java.util.concurrent.ForkJoinPool",
        "java.util.concurrent.ScheduledThreadPoolExecutor",
        "java.util.concurrent.ThreadPoolExecutor",
        "org.eclipse.jetty.util.thread.QueuedThreadPool",
        "org.eclipse.jetty.util.thread.ReservedThreadExecutor",
        "org.glassfish.grizzly.threadpool.GrizzlyExecutorService",
        "play.api.libs.streams.Execution$trampoline$",
        "scala.concurrent.forkjoin.ForkJoinPool",
        "scala.concurrent.Future$InternalCallbackExecutor$",
        "scala.concurrent.impl.ExecutionContextImpl",
      };

      Set<String> executors = new HashSet<>(Config.get().getListProperty(TRACE_EXECUTORS_CONFIG));
      executors.addAll(Arrays.asList(allowed));

      ALLOWED_EXECUTORS = Collections.unmodifiableSet(executors);

      String[] allowedPrefixes = {"slick.util.AsyncExecutor$"};
      ALLOWED_EXECUTORS_PREFIXES =
          Collections.unmodifiableCollection(Arrays.asList(allowedPrefixes));
    }
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    ElementMatcher.Junction<TypeDescription> matcher = any();
    final ElementMatcher.Junction<TypeDescription> hasExecutorInterfaceMatcher =
        implementsInterface(named(Executor.class.getName()));
    if (!TRACE_ALL_EXECUTORS) {
      matcher =
          matcher.and(
              new ElementMatcher<TypeDescription>() {
                @Override
                public boolean matches(TypeDescription target) {
                  boolean allowed = ALLOWED_EXECUTORS.contains(target.getName());

                  // Check for possible prefixes match only if not allowed already
                  if (!allowed) {
                    for (String name : ALLOWED_EXECUTORS_PREFIXES) {
                      if (target.getName().startsWith(name)) {
                        allowed = true;
                        break;
                      }
                    }
                  }

                  if (!allowed
                      && log.isDebugEnabled()
                      && hasExecutorInterfaceMatcher.matches(target)) {
                    log.debug("Skipping executor instrumentation for {}", target.getName());
                  }
                  return allowed;
                }
              });
    }
    return matcher.and(hasExecutorInterfaceMatcher); // Apply expensive matcher last.
  }
}
