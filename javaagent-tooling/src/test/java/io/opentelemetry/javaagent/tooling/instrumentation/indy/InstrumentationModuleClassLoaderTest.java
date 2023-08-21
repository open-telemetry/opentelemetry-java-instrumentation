/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.javaagent.tooling.instrumentation.indy.dummies.Bar;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.dummies.Foo;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.implementation.FixedValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("ClassNamedLikeTypeParameter")
class InstrumentationModuleClassLoaderTest {

  @Test
  void checkLookup() throws Throwable {
    Map<String, ClassCopySource> toInject = new HashMap<>();
    toInject.put(Foo.class.getName(), ClassCopySource.create(Foo.class));
    toInject.put(Bar.class.getName(), ClassCopySource.create(Bar.class));

    ClassLoader dummyParent = new URLClassLoader(new URL[] {}, null);

    InstrumentationModuleClassLoader m1 =
        new InstrumentationModuleClassLoader(dummyParent, dummyParent, toInject);
    InstrumentationModuleClassLoader m2 =
        new InstrumentationModuleClassLoader(dummyParent, dummyParent, toInject);

    // MethodHandles.publicLookup() always succeeds on the first invocation
    lookupAndInvokeFoo(m1);
    // MethodHandles.publicLookup() fails on the second invocation,
    // even though the classes are loaded from an isolated class loader hierarchy
    lookupAndInvokeFoo(m2);
  }

  private static void lookupAndInvokeFoo(InstrumentationModuleClassLoader classLoader)
      throws Throwable {
    Class<?> fooClass = classLoader.loadClass(Foo.class.getName());
    MethodHandles.Lookup lookup;
    // using public lookup fails with LinkageError on second invocation - is this a (known) JVM bug?
    // The failure only occurs on certain JVM versions, e.g. Java 8 and 11
    // lookup = MethodHandles.publicLookup();
    lookup = classLoader.getLookup();
    MethodHandle methodHandle =
        lookup.findStatic(
            fooClass,
            "foo",
            MethodType.methodType(String.class, classLoader.loadClass(Bar.class.getName())));
    assertThat(methodHandle.invoke((Bar) null)).isEqualTo("foo");
  }

  @Test
  void checkInjectedClassesHavePackage() throws Throwable {
    Map<String, ClassCopySource> toInject = new HashMap<>();
    toInject.put(A.class.getName(), ClassCopySource.create(A.class));
    toInject.put(B.class.getName(), ClassCopySource.create(B.class));
    String packageName = A.class.getName().substring(0, A.class.getName().lastIndexOf('.'));

    ClassLoader dummyParent = new URLClassLoader(new URL[] {}, null);
    InstrumentationModuleClassLoader m1 =
        new InstrumentationModuleClassLoader(dummyParent, dummyParent, toInject);

    Class<?> injected = Class.forName(A.class.getName(), true, m1);
    // inject two classes from the same package to trigger errors if we try to redefine the package
    Class.forName(B.class.getName(), true, m1);

    assertThat(injected.getClassLoader()).isSameAs(m1);
    Package clPackage = m1.findPackage(packageName);
    Package classPackage = injected.getPackage();

    assertThat(classPackage).isNotNull();
    assertThat(clPackage).isNotNull();
    assertThat(classPackage).isSameAs(clPackage);
  }

  @Test
  void checkClassLookupPrecedence(@TempDir Path tempDir) throws Exception {

    Map<String, byte[]> appClasses = copyClassesWithMarker("app-cl", A.class, B.class, C.class);
    Map<String, byte[]> agentClasses = copyClassesWithMarker("agent-cl", B.class, C.class);
    Map<String, byte[]> moduleClasses =
        copyClassesWithMarker("module-cl", A.class, B.class, C.class, D.class);

    Path appJar = tempDir.resolve("dummy-app.jar");
    createJar(appClasses, appJar);

    Path agentJar = tempDir.resolve("dummy-agent.jar");
    createJar(agentClasses, agentJar);

    Path moduleJar = tempDir.resolve("instrumentation-module.jar");
    createJar(moduleClasses, moduleJar);

    URLClassLoader appCl = new URLClassLoader(new URL[] {appJar.toUri().toURL()}, null);
    URLClassLoader agentCl = new URLClassLoader(new URL[] {agentJar.toUri().toURL()}, null);
    URLClassLoader moduleSourceCl = new URLClassLoader(new URL[] {moduleJar.toUri().toURL()}, null);

    try {
      Map<String, ClassCopySource> toInject = new HashMap<>();
      toInject.put(C.class.getName(), ClassCopySource.create(C.class.getName(), moduleSourceCl));

      InstrumentationModuleClassLoader moduleCl =
          new InstrumentationModuleClassLoader(appCl, agentCl, toInject);

      // Verify precedence for classloading
      Class<?> clA = moduleCl.loadClass(A.class.getName());
      assertThat(getMarkerValue(clA)).isEqualTo("app-cl");
      assertThat(clA.getClassLoader()).isSameAs(appCl);

      Class<?> clB = moduleCl.loadClass(B.class.getName());
      assertThat(getMarkerValue(clB)).isEqualTo("agent-cl");
      assertThat(clB.getClassLoader()).isSameAs(agentCl);

      Class<?> clC = moduleCl.loadClass(C.class.getName());
      assertThat(getMarkerValue(clC)).isEqualTo("module-cl");
      assertThat(clC.getClassLoader())
          .isSameAs(moduleCl); // class must be copied, therefore moduleCL

      assertThatThrownBy(() -> moduleCl.loadClass(D.class.getName()))
          .isInstanceOf(ClassNotFoundException.class);

      // Verify precedence for looking up .class resources
      URL resourceA = moduleCl.getResource(getClassFile(A.class));
      assertThat(resourceA.toString()).startsWith("jar:file:" + appJar);
      assertThat(Collections.list(moduleCl.getResources(getClassFile(A.class))))
          .containsExactly(resourceA);
      assertThat(moduleCl.getResourceAsStream(getClassFile(A.class)))
          .hasBinaryContent(appClasses.get(A.class.getName()));

      URL resourceB = moduleCl.getResource(getClassFile(B.class));
      assertThat(resourceB.toString()).startsWith("jar:file:" + agentJar);
      assertThat(Collections.list(moduleCl.getResources(getClassFile(B.class))))
          .containsExactly(resourceB);
      assertThat(moduleCl.getResourceAsStream(getClassFile(B.class)))
          .hasBinaryContent(agentClasses.get(B.class.getName()));

      URL resourceC = moduleCl.getResource(getClassFile(C.class));
      assertThat(resourceC.toString()).startsWith("jar:file:" + moduleJar);
      assertThat(Collections.list(moduleCl.getResources(getClassFile(C.class))))
          .containsExactly(resourceC);
      assertThat(moduleCl.getResourceAsStream(getClassFile(C.class)))
          .hasBinaryContent(moduleClasses.get(C.class.getName()));
      assertThat(moduleCl.getResource("/" + getClassFile(C.class))).isEqualTo(resourceC);

      assertThat(moduleCl.getResource(D.class.getName())).isNull();
      assertThat(moduleCl.getResourceAsStream(D.class.getName())).isNull();
      assertThat(Collections.list(moduleCl.getResources(D.class.getName()))).isEmpty();

      // And finally verify that our resource handling does what it is supposed to do:
      // Provide the correct bytecode sources when looking up bytecode with bytebuddy (or similar
      // tools)

      assertThat(ClassFileLocator.ForClassLoader.read(clA))
          .isEqualTo(appClasses.get(A.class.getName()));
      assertThat(ClassFileLocator.ForClassLoader.read(clB))
          .isEqualTo(agentClasses.get(B.class.getName()));
      assertThat(ClassFileLocator.ForClassLoader.read(clC))
          .isEqualTo(moduleClasses.get(C.class.getName()));

    } finally {
      appCl.close();
      agentCl.close();
      moduleSourceCl.close();
    }
  }

  private static String getClassFile(Class<?> cl) {
    return cl.getName().replace('.', '/') + ".class";
  }

  private static Map<String, byte[]> copyClassesWithMarker(String marker, Class<?>... toCopy) {

    Map<String, byte[]> classes = new HashMap<>();
    for (Class<?> clazz : toCopy) {
      classes.put(clazz.getName(), copyClassWithMarker(clazz, marker));
    }
    return classes;
  }

  private static byte[] copyClassWithMarker(Class<?> original, String markerValue) {
    return new ByteBuddy()
        .redefine(original)
        .defineMethod("marker", String.class, Modifier.PUBLIC | Modifier.STATIC)
        .intercept(FixedValue.value(markerValue))
        .make()
        .getBytes();
  }

  private static String getMarkerValue(Class<?> clazz) {
    try {
      return (String) clazz.getMethod("marker").invoke(null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void createJar(Map<String, byte[]> classNameToBytecode, Path jarFilePath) {
    try (OutputStream fileOut = Files.newOutputStream(jarFilePath)) {
      try (JarOutputStream jarOut = new JarOutputStream(fileOut)) {
        for (String clName : classNameToBytecode.keySet()) {
          String classFile = clName.replace('.', '/') + ".class";
          jarOut.putNextEntry(new JarEntry(classFile));
          jarOut.write(classNameToBytecode.get(clName));
          jarOut.closeEntry();
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class A {}

  public static class B {}

  public static class C {}

  public static class D {}
}
