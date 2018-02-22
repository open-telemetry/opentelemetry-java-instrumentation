package datadog.trace.agent.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTracer;
import datadog.opentracing.decorators.AbstractDecorator;
import datadog.opentracing.decorators.DDDecoratorsFactory;
import datadog.trace.agent.tooling.AgentInstaller;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.common.writer.ListWriter;
import io.opentracing.Tracer;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
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
@Slf4j
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
  private static ClassFileTransformer activeTransformer = null;

  protected static final Phaser WRITER_PHASER = new Phaser();

  static {
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

    final List<AbstractDecorator> decorators = DDDecoratorsFactory.createBuiltinDecorators();
    for (final AbstractDecorator decorator : decorators) {
      ((DDTracer) TEST_TRACER).addDecorator(decorator);
    }
    ByteBuddyAgent.install();
    instrumentation = ByteBuddyAgent.getInstrumentation();
  }

  @BeforeClass
  public static synchronized void agentSetup() {
    if (null != activeTransformer) {
      throw new IllegalStateException("transformer already in place: " + activeTransformer);
    }

    activeTransformer =
        AgentInstaller.installBytebuddyAgent(instrumentation, new ErrorCountingListener());
    TestUtils.registerOrReplaceGlobalTracer(TEST_TRACER);
  }

  @Before
  public void beforeTest() {
    TEST_WRITER.start();
    INSTRUMENTATION_ERROR_COUNT.set(0);
    assert TEST_TRACER.activeSpan() == null;
  }

  @After
  public void afterTest() {
    assert INSTRUMENTATION_ERROR_COUNT.get() == 0;
  }

  @AfterClass
  public static synchronized void agentClenup() {
    instrumentation.removeTransformer(activeTransformer);
    activeTransformer = null;
  }

  private static class ErrorCountingListener implements AgentBuilder.Listener {
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
