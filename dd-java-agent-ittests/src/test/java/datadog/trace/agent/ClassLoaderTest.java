package datadog.trace.agent;

import static datadog.trace.agent.test.IntegrationTestUtils.createJarWithClasses;

import datadog.trace.api.Trace;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.Assert;
import org.junit.Test;

// TODO: move to spock
public class ClassLoaderTest {

  /** Assert that we can instrument classloaders which cannot resolve agent advice classes. */
  @Test
  public void instrumentClassLoadersWithoutAgentClasses() throws Exception {
    final URL[] classpath = new URL[] {createJarWithClasses(ClassToInstrument.class, Trace.class)};
    final URLClassLoader loader = new URLClassLoader(classpath, null);

    try {
      loader.loadClass("datadog.agent.TracingAgent");
      Assert.fail("loader should not see agent classes.");
    } catch (final ClassNotFoundException cnfe) {
      // Good. loader can't see agent classes.
    }

    final Class<?> instrumentedClass = loader.loadClass(ClassToInstrument.class.getName());
    Assert.assertEquals(
        "Class must be loaded by loader.", loader, instrumentedClass.getClassLoader());

    loader.close();
  }

  public static class ClassToInstrument {
    @Trace
    public static void someMethod() {}
  }

  // TODO: Write test: assert our agent resource locator can locate resources from the bootstrap jar

  // TODO: Write test: ensure all classes are available on java 9 (vs the platform loader)
}
