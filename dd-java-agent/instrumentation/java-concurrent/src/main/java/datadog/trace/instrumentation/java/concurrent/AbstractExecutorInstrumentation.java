package datadog.trace.instrumentation.java.concurrent;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import datadog.trace.agent.tooling.Instrumenter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
public abstract class AbstractExecutorInstrumentation extends Instrumenter.Default {

  public static final String EXEC_NAME = "java_concurrent";

  /**
   * Only apply executor instrumentation to whitelisted executors. In the future, this restriction
   * may be lifted to include all executors.
   */
  private static final Collection<String> WHITELISTED_EXECUTORS;
  /**
   * Some frameworks have their executors defined as anon classes inside other classes. Referencing
   * anon classes by name would be fragile, so instead we will use list of class prefix names. Since
   * checking this list is more expensive (O(n)) we should try to keep it short.
   */
  private static final Collection<String> WHITELISTED_EXECUTORS_PREFIXES;

  static {
    final String[] whitelist = {
      "java.util.concurrent.AbstractExecutorService",
      "java.util.concurrent.ThreadPoolExecutor",
      "java.util.concurrent.ScheduledThreadPoolExecutor",
      "java.util.concurrent.ForkJoinPool",
      "java.util.concurrent.Executors$FinalizableDelegatedExecutorService",
      "java.util.concurrent.Executors$DelegatedExecutorService",
      "javax.management.NotificationBroadcasterSupport$1",
      "kotlinx.coroutines.scheduling.CoroutineScheduler",
      "scala.concurrent.Future$InternalCallbackExecutor$",
      "scala.concurrent.impl.ExecutionContextImpl",
      "scala.concurrent.impl.ExecutionContextImpl$$anon$1",
      "scala.concurrent.forkjoin.ForkJoinPool",
      "scala.concurrent.impl.ExecutionContextImpl$$anon$3",
      "akka.dispatch.MessageDispatcher",
      "akka.dispatch.Dispatcher",
      "akka.dispatch.Dispatcher$LazyExecutorServiceDelegate",
      "akka.actor.ActorSystemImpl$$anon$1",
      "akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinPool",
      "akka.dispatch.forkjoin.ForkJoinPool",
      "akka.dispatch.BalancingDispatcher",
      "akka.dispatch.ThreadPoolConfig$ThreadPoolExecutorServiceFactory$$anon$1",
      "akka.dispatch.PinnedDispatcher",
      "akka.dispatch.ExecutionContexts$sameThreadExecutionContext$",
      "play.api.libs.streams.Execution$trampoline$",
      "io.netty.channel.MultithreadEventLoopGroup",
      "io.netty.util.concurrent.MultithreadEventExecutorGroup",
      "io.netty.util.concurrent.AbstractEventExecutorGroup",
      "io.netty.channel.epoll.EpollEventLoopGroup",
      "io.netty.channel.nio.NioEventLoopGroup",
      "io.netty.util.concurrent.GlobalEventExecutor",
      "io.netty.util.concurrent.AbstractScheduledEventExecutor",
      "io.netty.util.concurrent.AbstractEventExecutor",
      "io.netty.util.concurrent.SingleThreadEventExecutor",
      "io.netty.channel.nio.NioEventLoop",
      "io.netty.channel.SingleThreadEventLoop",
    };
    WHITELISTED_EXECUTORS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(whitelist)));

    final String[] whitelistPrefixes = {"slick.util.AsyncExecutor$"};
    WHITELISTED_EXECUTORS_PREFIXES =
        Collections.unmodifiableCollection(Arrays.asList(whitelistPrefixes));
  }

  public AbstractExecutorInstrumentation() {
    super(EXEC_NAME);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named(Executor.class.getName())))
        .and(
            new ElementMatcher<TypeDescription>() {
              @Override
              public boolean matches(final TypeDescription target) {
                boolean whitelisted = WHITELISTED_EXECUTORS.contains(target.getName());

                // Check for possible prefixes match only if not whitelisted already
                if (!whitelisted) {
                  for (final String name : WHITELISTED_EXECUTORS_PREFIXES) {
                    if (target.getName().startsWith(name)) {
                      whitelisted = true;
                      break;
                    }
                  }
                }

                if (!whitelisted) {
                  log.debug("Skipping executor instrumentation for {}", target.getName());
                }
                return whitelisted;
              }
            });
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      AbstractExecutorInstrumentation.class.getPackage().getName() + ".ExecutorInstrumentationUtils"
    };
  }
}
