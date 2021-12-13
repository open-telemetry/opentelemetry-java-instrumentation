/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.emptyList;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
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

public abstract class AbstractExecutorInstrumentation implements TypeInstrumentation {
  private static final Logger logger =
      LoggerFactory.getLogger(AbstractExecutorInstrumentation.class);

  private static final String EXECUTORS_INCLUDE_PROPERTY_NAME =
      "otel.instrumentation.executors.include";

  private static final String EXECUTORS_INCLUDE_ALL_PROPERTY_NAME =
      "otel.instrumentation.executors.include-all";

  private static final boolean INCLUDE_ALL =
      Config.get().getBoolean(EXECUTORS_INCLUDE_ALL_PROPERTY_NAME, false);

  /**
   * Only apply executor instrumentation to allowed executors. To apply to all executors, use
   * override setting above.
   */
  private final Collection<String> includeExecutors;

  /**
   * Some frameworks have their executors defined as anon classes inside other classes. Referencing
   * anon classes by name would be fragile, so instead we will use list of class prefix names. Since
   * checking this list is more expensive (O(n)) we should try to keep it short.
   */
  private final Collection<String> includePrefixes;

  protected AbstractExecutorInstrumentation() {
    if (INCLUDE_ALL) {
      includeExecutors = Collections.emptyList();
      includePrefixes = Collections.emptyList();
    } else {
      String[] includeExecutors = {
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
        "org.apache.tomcat.util.threads.ThreadPoolExecutor",
        "org.eclipse.jetty.util.thread.QueuedThreadPool", // dispatch() covered in the jetty module
        "org.eclipse.jetty.util.thread.ReservedThreadExecutor",
        "org.glassfish.grizzly.threadpool.GrizzlyExecutorService",
        "org.jboss.threads.EnhancedQueueExecutor",
        "play.api.libs.streams.Execution$trampoline$",
        "play.shaded.ahc.io.netty.util.concurrent.ThreadPerTaskExecutor",
        "scala.concurrent.forkjoin.ForkJoinPool",
        "scala.concurrent.Future$InternalCallbackExecutor$",
        "scala.concurrent.impl.ExecutionContextImpl",
      };
      Set<String> combined = new HashSet<>(Arrays.asList(includeExecutors));
      combined.addAll(Config.get().getList(EXECUTORS_INCLUDE_PROPERTY_NAME, emptyList()));
      this.includeExecutors = Collections.unmodifiableSet(combined);

      String[] includePrefixes = {"slick.util.AsyncExecutor$"};
      this.includePrefixes = Collections.unmodifiableCollection(Arrays.asList(includePrefixes));
    }
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    ElementMatcher.Junction<TypeDescription> matcher = any();
    ElementMatcher.Junction<TypeDescription> hasExecutorInterfaceMatcher =
        implementsInterface(named(Executor.class.getName()));
    if (!INCLUDE_ALL) {
      matcher =
          matcher.and(
              new ElementMatcher<TypeDescription>() {
                @Override
                public boolean matches(TypeDescription target) {
                  boolean allowed = includeExecutors.contains(target.getName());

                  // Check for possible prefixes match only if not allowed already
                  if (!allowed) {
                    for (String name : includePrefixes) {
                      if (target.getName().startsWith(name)) {
                        allowed = true;
                        break;
                      }
                    }
                  }

                  if (!allowed
                      && logger.isDebugEnabled()
                      && hasExecutorInterfaceMatcher.matches(target)) {
                    logger.debug("Skipping executor instrumentation for {}", target.getName());
                  }
                  return allowed;
                }
              });
    }
    return matcher.and(hasExecutorInterfaceMatcher); // Apply expensive matcher last.
  }
}
