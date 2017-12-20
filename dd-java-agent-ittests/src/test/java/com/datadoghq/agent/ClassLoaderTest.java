package com.datadoghq.agent;

import static dd.test.TestUtils.createJarWithClasses;

import com.datadoghq.trace.Trace;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.Assert;
import org.junit.Test;

public class ClassLoaderTest {

  /** Assert that we can instrument classloaders which cannot resolve agent advice classes. */
  @Test
  public void instrumentClassLoadersWithoutAgentClasses() throws Exception {
    URL[] classpath = new URL[] {createJarWithClasses(ClassToInstrument.class, Trace.class)};
    URLClassLoader loader = new URLClassLoader(classpath, null);

    try {
      loader.loadClass("com.datadoghq.agent.TracingAgent");
      Assert.fail("loader should not see agent classes.");
    } catch (ClassNotFoundException cnfe) {
      // Good. loader can't see agent classes.
    }

    Class<?> instrumentedClass = loader.loadClass(ClassToInstrument.class.getName());
    Assert.assertEquals(
        "Class must be loaded by loader.", loader, instrumentedClass.getClassLoader());

    loader.close();
  }

  public static class ClassToInstrument {
    @Trace
    public static void someMethod() {}
  }
}
