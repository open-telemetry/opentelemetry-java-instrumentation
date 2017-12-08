package com.datadoghq.agent;

import com.datadoghq.trace.Trace;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
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

    final Class<?> rulesManagerClass =
        Class.forName(
            "com.datadoghq.agent.InstrumentationRulesManager",
            true,
            ClassLoader.getSystemClassLoader());
    Method isRegisteredMethod = rulesManagerClass.getMethod("isRegistered", Object.class);
    Assert.assertTrue(
        "Agent did not initialized loader.", (boolean) isRegisteredMethod.invoke(null, loader));
    loader.close();
  }

  /** com.foo.Bar -> com/foo/Bar.class */
  public static String getResourceName(Class<?> clazz) {
    return clazz.getName().replace('.', '/') + ".class";
  }

  /**
   * Create a temporary jar on the filesystem with the bytes of the given classes.
   *
   * <p>The jar file will be removed when the jvm exits.
   *
   * @param classes classes to package into the jar.
   * @return the location of the newly created jar.
   * @throws IOException
   */
  public static URL createJarWithClasses(Class<?>... classes) throws IOException {
    final File tmpJar = File.createTempFile(UUID.randomUUID() + "", ".jar");
    tmpJar.deleteOnExit();

    final Manifest manifest = new Manifest();
    JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar), manifest);
    for (Class<?> clazz : classes) {
      addToJar(clazz, target);
    }
    target.close();

    return tmpJar.toURI().toURL();
  }

  private static void addToJar(Class<?> clazz, JarOutputStream jarOutputStream) throws IOException {
    InputStream inputStream = null;
    try {
      JarEntry entry = new JarEntry(getResourceName(clazz));
      jarOutputStream.putNextEntry(entry);
      inputStream = clazz.getClassLoader().getResourceAsStream(getResourceName(clazz));

      byte[] buffer = new byte[1024];
      while (true) {
        int count = inputStream.read(buffer);
        if (count == -1) {
          break;
        }
        jarOutputStream.write(buffer, 0, count);
      }
      jarOutputStream.closeEntry();
    } finally {
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }

  public static class ClassToInstrument {
    @Trace
    public static void someMethod() {}
  }
}
