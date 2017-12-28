package dd.test;

import com.datadoghq.agent.AgentInstaller;
import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.writer.ListWriter;
import io.opentracing.Tracer;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.spockframework.runtime.Sputnik;
import org.spockframework.runtime.model.SpecMetadata;
import spock.lang.Specification;

@RunWith(AgentTestRunner.SpockRunner.class)
@SpecMetadata(filename = "AgentTestRunner.java", line = 0)
public abstract class AgentTestRunner extends Specification {
  /**
   * For test runs, agent's global tracer will report to this list writer.
   *
   * <p>Before the start of each test the reported traces will be reset.
   */
  public static final ListWriter TEST_WRITER;

  private static final Tracer TEST_TRACER;
  private static final Instrumentation instrumentation;
  private static ClassFileTransformer activeTransformer = null;

  static {
    TEST_WRITER = new ListWriter();
    TEST_TRACER = new DDTracer(TEST_WRITER);
    ByteBuddyAgent.install();
    instrumentation = ByteBuddyAgent.getInstrumentation();
  }

  @BeforeClass
  public static synchronized void agentSetup() {
    if (null != activeTransformer) {
      throw new IllegalStateException("transformer already in place: " + activeTransformer);
    }
    activeTransformer = AgentInstaller.installBytebuddyAgent(instrumentation);
    TestUtils.registerOrReplaceGlobalTracer(TEST_TRACER);
  }

  @Before
  public void beforeTest() {
    TEST_WRITER.start();
  }

  @AfterClass
  public static synchronized void agentClenup() {
    instrumentation.removeTransformer(activeTransformer);
    activeTransformer = null;
  }

  // FIXME: Remove SpockRunner and custom classload logic

  public static class SpockRunner extends Sputnik {
    private final InstrumentationClassLoader customLoader;

    public SpockRunner(Class<?> clazz)
        throws InitializationError, NoSuchFieldException, SecurityException,
            IllegalArgumentException, IllegalAccessException {
      super(shadowTestClass(clazz));
      // access the classloader created in shadowTestClass above
      Field clazzField = Sputnik.class.getDeclaredField("clazz");
      try {
        clazzField.setAccessible(true);
        customLoader =
            (InstrumentationClassLoader) ((Class<?>) clazzField.get(this)).getClassLoader();
      } finally {
        clazzField.setAccessible(false);
      }
    }

    // Shadow the test class with bytes loaded by InstrumentationClassLoader
    private static Class<?> shadowTestClass(final Class<?> clazz) {
      try {
        InstrumentationClassLoader customLoader =
            new InstrumentationClassLoader(SpockRunner.class.getClassLoader());
        return customLoader.shadow(clazz);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    // Replace the context class loader for each test with InstrumentationClassLoader
    @Override
    public void run(final RunNotifier notifier) {
      final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(customLoader);
        super.run(notifier);
      } finally {
        Thread.currentThread().setContextClassLoader(contextLoader);
      }
    }
  }

  /**
   * A ClassLoader which retransforms classes unseen by the installed agent. With the exception of
   * shadowed classes, this class
   */
  private static class InstrumentationClassLoader extends java.lang.ClassLoader {
    final ClassLoader parent;

    public InstrumentationClassLoader(ClassLoader parent) {
      super(parent);
      this.parent = parent;
    }

    /** Forcefully inject the bytes of clazz into this classloader. */
    public Class<?> shadow(Class<?> clazz) throws IOException {
      final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(clazz.getClassLoader());
      final byte[] classBytes = locator.locate(clazz.getName()).resolve();

      Class<?> shadowed = this.defineClass(clazz.getName(), classBytes, 0, classBytes.length);
      return shadowed;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      // TODO: If already loaded and not seen by agent: do a retransform.
      return parent.loadClass(name);
    }
  }
}
