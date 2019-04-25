package datadog.trace.agent.test;

import com.google.common.reflect.ClassPath;
import datadog.trace.agent.test.utils.ClasspathUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.spockframework.runtime.Sputnik;

/**
 * Runs a spock test in an agent-friendly way.
 *
 * <ul>
 *   <li>Adds agent bootstrap classes to bootstrap classpath.
 * </ul>
 */
public class SpockRunner extends Sputnik {
  /**
   * An exact copy of {@link datadog.trace.agent.tooling.Constants#BOOTSTRAP_PACKAGE_PREFIXES}.
   *
   * <p>This list is needed to initialize the bootstrap classpath because Utils' static initializer
   * references bootstrap classes (e.g. DatadogClassLoader).
   */
  public static final String[] BOOTSTRAP_PACKAGE_PREFIXES_COPY = {
    "datadog.slf4j",
    "datadog.trace.api",
    "datadog.trace.bootstrap",
    "datadog.trace.context",
    "io.opentracing",
  };

  private static final String[] TEST_BOOTSTRAP_PREFIXES;

  static {
    ByteBuddyAgent.install();
    final String[] testBS = {
      "io.opentracing",
      "org.slf4j",
      "ch.qos.logback",
      // Tomcat's servlet classes must be on boostrap
      // when running tomcat test
      "javax.servlet.ServletContainerInitializer",
      "javax.servlet.ServletContext"
    };
    TEST_BOOTSTRAP_PREFIXES =
        Arrays.copyOf(
            BOOTSTRAP_PACKAGE_PREFIXES_COPY,
            BOOTSTRAP_PACKAGE_PREFIXES_COPY.length + testBS.length);
    for (int i = 0; i < testBS.length; ++i) {
      TEST_BOOTSTRAP_PREFIXES[i + BOOTSTRAP_PACKAGE_PREFIXES_COPY.length] = testBS[i];
    }

    setupBootstrapClasspath();
  }

  private final InstrumentationClassLoader customLoader;

  public SpockRunner(final Class<?> clazz)
      throws InitializationError, NoSuchFieldException, SecurityException, IllegalArgumentException,
          IllegalAccessException {
    super(shadowTestClass(clazz));
    assertNoBootstrapClassesInTestClass(clazz);
    // access the classloader created in shadowTestClass above
    final Field clazzField = Sputnik.class.getDeclaredField("clazz");
    try {
      clazzField.setAccessible(true);
      customLoader =
          (InstrumentationClassLoader) ((Class<?>) clazzField.get(this)).getClassLoader();
    } finally {
      clazzField.setAccessible(false);
    }
  }

  private static void assertNoBootstrapClassesInTestClass(final Class<?> testClass) {
    for (final Field field : testClass.getDeclaredFields()) {
      assertNotBootstrapClass(testClass, field.getType());
    }

    for (final Method method : testClass.getDeclaredMethods()) {
      assertNotBootstrapClass(testClass, method.getReturnType());
      for (final Class paramType : method.getParameterTypes()) {
        assertNotBootstrapClass(testClass, paramType);
      }
    }
  }

  private static void assertNotBootstrapClass(final Class<?> testClass, final Class<?> clazz) {
    if ((!clazz.isPrimitive()) && isBootstrapClass(clazz.getName())) {
      throw new IllegalStateException(
          testClass.getName()
              + ": Bootstrap classes are not allowed in test class field or method signatures. Offending class: "
              + clazz.getName());
    }
  }

  private static boolean isBootstrapClass(final String className) {
    for (int i = 0; i < TEST_BOOTSTRAP_PREFIXES.length; ++i) {
      if (className.startsWith(TEST_BOOTSTRAP_PREFIXES[i])) {
        return true;
      }
    }
    return false;
  }

  // Shadow the test class with bytes loaded by InstrumentationClassLoader
  private static Class<?> shadowTestClass(final Class<?> clazz) {
    try {
      final InstrumentationClassLoader customLoader =
          new InstrumentationClassLoader(
              datadog.trace.agent.test.SpockRunner.class.getClassLoader(), clazz.getName());
      return customLoader.shadow(clazz);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

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

  private static void setupBootstrapClasspath() {
    try {
      final File bootstrapJar = createBootstrapJar();
      ByteBuddyAgent.getInstrumentation()
          .appendToBootstrapClassLoaderSearch(new JarFile(bootstrapJar));
      // Utils cannot be referenced before this line, as its static initializers load bootstrap
      // classes (for example, the bootstrap proxy).
      datadog.trace.agent.tooling.Utils.getBootstrapProxy().addURL(bootstrapJar.toURI().toURL());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static File createBootstrapJar() throws IOException {
    final Set<String> bootstrapClasses = new HashSet<>();
    for (final ClassPath.ClassInfo info : ClasspathUtils.getTestClasspath().getAllClasses()) {
      // if info starts with bootstrap prefix: add to bootstrap jar
      if (isBootstrapClass(info.getName())) {
        bootstrapClasses.add(info.getResourceName());
      }
    }
    return new File(
        ClasspathUtils.createJarWithClasses(
                AgentTestRunner.class.getClassLoader(), bootstrapClasses.toArray(new String[0]))
            .getFile());
  }

  /** Run test classes in a classloader which loads test classes before delegating. */
  private static class InstrumentationClassLoader extends java.lang.ClassLoader {
    final ClassLoader parent;
    final String shadowPrefix;

    public InstrumentationClassLoader(final ClassLoader parent, final String shadowPrefix) {
      super(parent);
      this.parent = parent;
      this.shadowPrefix = shadowPrefix;
    }

    /** Forcefully inject the bytes of clazz into this classloader. */
    public Class<?> shadow(final Class<?> clazz) throws IOException {
      final Class<?> loaded = findLoadedClass(clazz.getName());
      if (loaded != null && loaded.getClassLoader() == this) {
        return loaded;
      }
      final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(clazz.getClassLoader());
      final byte[] classBytes = locator.locate(clazz.getName()).resolve();

      return defineClass(clazz.getName(), classBytes, 0, classBytes.length);
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve)
        throws ClassNotFoundException {
      synchronized (super.getClassLoadingLock(name)) {
        final Class c = findLoadedClass(name);
        if (c != null) {
          return c;
        }
        if (name.startsWith(shadowPrefix)) {
          try {
            return shadow(super.loadClass(name, resolve));
          } catch (final Exception e) {
          }
        }

        return parent.loadClass(name);
      }
    }
  }
}
