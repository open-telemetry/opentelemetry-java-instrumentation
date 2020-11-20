/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
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

abstract class AbstractExecutorInstrumentation implements TypeInstrumentation {
  private static final Logger log = LoggerFactory.getLogger(AbstractExecutorInstrumentation.class);

  private static final String TRACE_EXECUTORS_CONFIG = "otel.trace.executors";

  // hopefully these configuration properties can be static after
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1345
  private final boolean traceAllExecutors =
      Config.get().getBooleanProperty("otel.trace.executors.all", false);

  /**
   * Only apply executor instrumentation to allowed executors. To apply to all executors, use
   * override setting above.
   */
  private final Collection<String> allowedExecutors;

  /**
   * Some frameworks have their executors defined as anon classes inside other classes. Referencing
   * anon classes by name would be fragile, so instead we will use list of class prefix names. Since
   * checking this list is more expensive (O(n)) we should try to keep it short.
   */
  private final Collection<String> allowedExecutorsPrefixes;

  AbstractExecutorInstrumentation() {
    if (traceAllExecutors) {
      log.info("Tracing all executors enabled.");
      allowedExecutors = Collections.emptyList();
      allowedExecutorsPrefixes = Collections.emptyList();
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

      allowedExecutors = Collections.unmodifiableSet(executors);

      String[] allowedPrefixes = {"slick.util.AsyncExecutor$"};
      allowedExecutorsPrefixes = Collections.unmodifiableCollection(Arrays.asList(allowedPrefixes));
    }
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    ElementMatcher.Junction<TypeDescription> matcher = any();
    final ElementMatcher.Junction<TypeDescription> hasExecutorInterfaceMatcher =
        implementsInterface(named(Executor.class.getName()));
    if (!traceAllExecutors) {
      matcher =
          matcher.and(
              new ElementMatcher<TypeDescription>() {
                @Override
                public boolean matches(TypeDescription target) {
                  boolean allowed = allowedExecutors.contains(target.getName());

                  // Check for possible prefixes match only if not allowed already
                  if (!allowed) {
                    for (String name : allowedExecutorsPrefixes) {
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
