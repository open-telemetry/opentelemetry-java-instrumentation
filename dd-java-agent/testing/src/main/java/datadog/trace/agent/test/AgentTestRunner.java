package datadog.trace.agent.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTracer;
import datadog.trace.agent.tooling.AgentInstaller;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.common.writer.ListWriter;
import io.opentracing.Tracer;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.*;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.model.SpecMetadata;
import spock.lang.Specification;

/**
 * A spock test runner which automatically applies instrumentation and exposes a global trace
 * writer.
 *
 * <p>To use, write a regular spock test, but extend this class instead of {@link
 * spock.lang.Specification}. <br>
 * This will cause the following to occur before test startup:
 *
 * <ul>
 *   <li>All {@link Instrumenter}s on the test classpath will be applied. Matching preloaded classes
 *       will be retransformed.
 *   <li>{@link AgentTestRunner#TEST_WRITER} will be registerd with the global tracer and available
 *       in an initialized state.
 * </ul>
 */
@RunWith(SpockRunner.class)
@SpecMetadata(filename = "AgentTestRunner.java", line = 0)
public abstract class AgentTestRunner extends Specification {
  /**
   * For test runs, agent's global tracer will report to this list writer.
   *
   * <p>Before the start of each test the reported traces will be reset.
   */
  public static final ListWriter TEST_WRITER;

  protected static final Tracer TEST_TRACER;
  private static final AtomicInteger INSTRUMENTATION_ERROR_COUNT = new AtomicInteger();

  private static final Instrumentation instrumentation;
  private static volatile ClassFileTransformer activeTransformer = null;

  protected static final Phaser WRITER_PHASER = new Phaser();

  static {
    instrumentation = ByteBuddyAgent.getInstrumentation();

    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    ((Logger) LoggerFactory.getLogger("datadog")).setLevel(Level.DEBUG);

    WRITER_PHASER.register();
    TEST_WRITER =
        new ListWriter() {
          @Override
          public boolean add(final List<DDSpan> trace) {
            final boolean result = super.add(trace);
            WRITER_PHASER.arrive();
            return result;
          }
        };
    TEST_TRACER = new DDTracer(TEST_WRITER);
    TestUtils.registerOrReplaceGlobalTracer(TEST_TRACER);
  }

  @BeforeClass
  public static synchronized void agentSetup() throws Exception {
    if (null != activeTransformer) {
      throw new IllegalStateException("transformer already in place: " + activeTransformer);
    }

    final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(AgentTestRunner.class.getClassLoader());
      activeTransformer =
          AgentInstaller.installBytebuddyAgent(instrumentation, new ErrorCountingListener());
    } finally {
      Thread.currentThread().setContextClassLoader(contextLoader);
    }
  }

  @Before
  public void beforeTest() {
    TEST_WRITER.start();
    INSTRUMENTATION_ERROR_COUNT.set(0);
    assert (TEST_TRACER).activeSpan() == null;
  }

  @After
  public void afterTest() {
    assert INSTRUMENTATION_ERROR_COUNT.get() == 0;
  }

  @AfterClass
  public static synchronized void agentClenup() {
    if (null != activeTransformer) {
      instrumentation.removeTransformer(activeTransformer);
      activeTransformer = null;
    }
  }

  public static class ErrorCountingListener implements AgentBuilder.Listener {
    @Override
    public void onDiscovery(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {}

    @Override
    public void onTransformation(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final DynamicType dynamicType) {}

    @Override
    public void onIgnored(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {}

    @Override
    public void onError(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final Throwable throwable) {
      // System.err.println("failed to instrument " + typeName);
      // throwable.printStackTrace();
      INSTRUMENTATION_ERROR_COUNT.incrementAndGet();
    }

    @Override
    public void onComplete(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {}
  }
}
