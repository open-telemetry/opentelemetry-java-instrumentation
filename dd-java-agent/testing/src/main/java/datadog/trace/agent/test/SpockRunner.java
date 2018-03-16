package datadog.trace.agent.test;

import static datadog.trace.agent.tooling.Utils.BOOTSTRAP_PACKAGE_PREFIXES;

import com.google.common.reflect.ClassPath;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
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
 *   <li> Adds agent bootstrap classes to bootstrap classpath.
 * </ul>
 */
public class SpockRunner extends Sputnik {
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
            BOOTSTRAP_PACKAGE_PREFIXES, BOOTSTRAP_PACKAGE_PREFIXES.length + testBS.length);
    for (int i = 0; i < testBS.length; ++i) {
      TEST_BOOTSTRAP_PREFIXES[i + BOOTSTRAP_PACKAGE_PREFIXES.length] = testBS[i];
    }

    setupBootstrapClasspath();
  }

  private final InstrumentationClassLoader customLoader;

  public SpockRunner(Class<?> clazz)
      throws InitializationError, NoSuchFieldException, SecurityException, IllegalArgumentException,
          IllegalAccessException {
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
          new InstrumentationClassLoader(
              datadog.trace.agent.test.SpockRunner.class.getClassLoader(), clazz.getName());
      return customLoader.shadow(clazz);
    } catch (Exception e) {
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
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static File createBootstrapJar() throws IOException {
    ClassLoader loader = AgentTestRunner.class.getClassLoader();
    if (!(loader instanceof URLClassLoader)) {
      // java9's system loader does not extend URLClassLoader
      // which breaks google ClassPath lookup
      Field f = null;
      try {
        f = loader.getClass().getDeclaredField("ucp");
        f.setAccessible(true);
        Object ucp = f.get(loader);
        loader = new URLClassLoader((URL[]) ucp.getClass().getMethod("getURLs").invoke(ucp), null);
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        if (null != f) {
          f.setAccessible(false);
        }
      }
    }
    final ClassPath testCP = ClassPath.from(loader);
    Set<String> bootstrapClasses = new HashSet<String>();
    for (ClassPath.ClassInfo info : testCP.getAllClasses()) {
      // if info starts with bootstrap prefix: add to bootstrap jar
      for (int i = 0; i < TEST_BOOTSTRAP_PREFIXES.length; ++i) {
        if (info.getName().startsWith(TEST_BOOTSTRAP_PREFIXES[i])) {
          bootstrapClasses.add(info.getResourceName());
          break;
        }
      }
    }
    return new File(
        TestUtils.createJarWithClasses(loader, bootstrapClasses.toArray(new String[0])).getFile());
  }

  /** Run test classes in a classloader which loads test classes before delegating. */
  private static class InstrumentationClassLoader extends java.lang.ClassLoader {
    final ClassLoader parent;
    final String shadowPrefix;

    public InstrumentationClassLoader(ClassLoader parent, String shadowPrefix) {
      super(parent);
      this.parent = parent;
      this.shadowPrefix = shadowPrefix;
    }

    /** Forcefully inject the bytes of clazz into this classloader. */
    public Class<?> shadow(Class<?> clazz) throws IOException {
      Class<?> loaded = this.findLoadedClass(clazz.getName());
      if (loaded != null && loaded.getClassLoader() == this) {
        return loaded;
      }
      final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(clazz.getClassLoader());
      final byte[] classBytes = locator.locate(clazz.getName()).resolve();

      return this.defineClass(clazz.getName(), classBytes, 0, classBytes.length);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (super.getClassLoadingLock(name)) {
        Class c = this.findLoadedClass(name);
        if (c != null) {
          return c;
        }
        if (name.startsWith(shadowPrefix)) {
          try {
            return shadow(super.loadClass(name, resolve));
          } catch (Exception e) {
          }
        }

      /*
      if (!name.startsWith("datadog.trace.agent.test.")) {
        for (int i = 0; i < AGENT_PACKAGE_PREFIXES.length; ++i) {
          if (name.startsWith(AGENT_PACKAGE_PREFIXES[i])) {
            throw new ClassNotFoundException(
                "refusing to load agent class: " + name + " on test classloader.");
          }
        }
      }
      */
        return parent.loadClass(name);
      }
    }
  }
}
