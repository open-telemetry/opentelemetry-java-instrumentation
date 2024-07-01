/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.FINE;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class ExecutorMatchers {

  private static final Logger logger = Logger.getLogger(ExecutorMatchers.class.getName());

  /**
   * Only apply executor instrumentation to allowed executors. To apply to all executors, use
   * override setting above.
   */
  private static final Set<String> INSTRUMENTED_EXECUTOR_NAMES;

  /**
   * Some frameworks have their executors defined as anon classes inside other classes. Referencing
   * anon classes by name would be fragile, so instead we will use list of class prefix names. Since
   * checking this list is more expensive (O(n)) we should try to keep it short.
   */
  private static final List<String> INSTRUMENTED_EXECUTOR_PREFIXES;

  static {
    Set<String> combined =
        new HashSet<>(
            Arrays.asList(
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
                "java.util.concurrent.ThreadPerTaskExecutor",
                "org.apache.tomcat.util.threads.ThreadPoolExecutor",
                "org.eclipse.jetty.util.thread.QueuedThreadPool", // dispatch() covered in the jetty
                // module
                "org.eclipse.jetty.util.thread.ReservedThreadExecutor",
                "org.glassfish.grizzly.threadpool.GrizzlyExecutorService",
                "org.jboss.threads.EnhancedQueueExecutor",
                "org.apache.pekko.dispatch.BalancingDispatcher",
                "org.apache.pekko.dispatch.Dispatcher",
                "org.apache.pekko.dispatch.Dispatcher$LazyExecutorServiceDelegate",
                "org.apache.pekko.dispatch.ExecutionContexts$sameThreadExecutionContext$",
                "org.apache.pekko.dispatch.ForkJoinExecutorConfigurator$PekkoForkJoinPool",
                "org.apache.pekko.dispatch.MessageDispatcher",
                "org.apache.pekko.dispatch.PinnedDispatcher",
                "play.api.libs.streams.Execution$trampoline$",
                "play.shaded.ahc.io.netty.util.concurrent.ThreadPerTaskExecutor",
                "scala.concurrent.forkjoin.ForkJoinPool",
                "scala.concurrent.Future$InternalCallbackExecutor$",
                "scala.concurrent.impl.ExecutionContextImpl"));
    combined.addAll(
        AgentInstrumentationConfig.get()
            .getList("otel.instrumentation.executors.include", emptyList()));
    INSTRUMENTED_EXECUTOR_NAMES = Collections.unmodifiableSet(combined);

    INSTRUMENTED_EXECUTOR_PREFIXES = Collections.singletonList("slick.util.AsyncExecutor$");
  }

  static ElementMatcher.Junction<TypeDescription> executorNameMatcher() {
    if (AgentInstrumentationConfig.get()
        .getBoolean("otel.instrumentation.executors.include-all", false)) {
      return any();
    }

    return new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
      @Override
      public boolean matches(TypeDescription target) {
        boolean allowed = INSTRUMENTED_EXECUTOR_NAMES.contains(target.getName());

        // Check for possible prefixes match only if not allowed already
        if (!allowed) {
          for (String name : INSTRUMENTED_EXECUTOR_PREFIXES) {
            if (target.getName().startsWith(name)) {
              allowed = true;
              break;
            }
          }
        }

        // only log the statement if we log that level of detail
        if (logger.isLoggable(FINE)) {
          if (!allowed && isExecutor().matches(target)) {
            logger.log(FINE, "Skipping executor instrumentation for {0}", target.getName());
          }
        }
        return allowed;
      }
    };
  }

  static ElementMatcher<TypeDescription> isExecutor() {
    return implementsInterface(named(Executor.class.getName()));
  }

  static ElementMatcher<TypeDescription> isThreadPoolExecutor() {
    return extendsClass(named(ThreadPoolExecutor.class.getName()));
  }

  private ExecutorMatchers() {}
}
