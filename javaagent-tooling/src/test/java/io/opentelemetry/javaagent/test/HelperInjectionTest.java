/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.test;

import static io.opentelemetry.instrumentation.test.utils.ClasspathUtils.isClassLoaded;
import static io.opentelemetry.instrumentation.test.utils.GcUtils.awaitGc;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.javaagent.bootstrap.InjectedClassHelper;
import io.opentelemetry.javaagent.tooling.AgentInstaller;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.Test;

@SuppressWarnings("UnnecessaryAsync")
class HelperInjectionTest {

  private static class EmptyLoader extends URLClassLoader {
    EmptyLoader() {
      super(new URL[0], null);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      // the same code as added by LoadInjectedClassInstrumentation
      InjectedClassHelper.HelperClassInfo helperClassInfo =
          InjectedClassHelper.getHelperClassInfo(this, name);
      if (helperClassInfo != null) {
        Class<?> clazz = findLoadedClass(name);
        if (clazz != null) {
          return clazz;
        }
        try {
          byte[] bytes = helperClassInfo.getClassBytes();
          return defineClass(name, bytes, 0, bytes.length, helperClassInfo.getProtectionDomain());
        } catch (LinkageError error) {
          clazz = findLoadedClass(name);
          if (clazz != null) {
            return clazz;
          }
          throw error;
        }
      }

      return super.loadClass(name, resolve);
    }
  }

  @Test
  void helpersInjectedToNonDelegatingClassloader() throws Exception {
    URL[] helpersSourceUrls = new URL[1];
    helpersSourceUrls[0] = HelperClass.class.getProtectionDomain().getCodeSource().getLocation();
    ClassLoader helpersSourceLoader = new URLClassLoader(helpersSourceUrls);

    String helperClassName = HelperInjectionTest.class.getPackage().getName() + ".HelperClass";
    HelperInjector injector =
        new HelperInjector(
            "test", singletonList(helperClassName), emptyList(), helpersSourceLoader, null);
    AtomicReference<EmptyLoader> emptyLoader = new AtomicReference<>(new EmptyLoader());

    assertThatThrownBy(() -> emptyLoader.get().loadClass(helperClassName))
        .isInstanceOf(ClassNotFoundException.class);

    injector.transform(null, null, emptyLoader.get(), null, null);
    emptyLoader.get().loadClass(helperClassName);

    assertThat(isClassLoaded(helperClassName, emptyLoader.get())).isTrue();
    // injecting into emptyLoader should not cause helper class to be load in the helper source
    // classloader
    assertThat(isClassLoaded(helperClassName, helpersSourceLoader)).isFalse();

    // references to emptyLoader are gone
    emptyLoader.get().close(); // cleanup
    WeakReference<URLClassLoader> ref = new WeakReference<>(emptyLoader.get());
    emptyLoader.set(null);

    awaitGc(ref, Duration.ofSeconds(10));

    // HelperInjector doesn't prevent it from being collected
    assertThat(ref.get()).isNull();
  }

  @Test
  void helpersInjectedOnBootstrapClassloader() throws Exception {
    ByteBuddyAgent.install();
    AgentInstaller.installBytebuddyAgent(
        ByteBuddyAgent.getInstrumentation(), this.getClass().getClassLoader());

    String helperClassName = HelperInjectionTest.class.getPackage().getName() + ".HelperClass";
    HelperInjector injector =
        new HelperInjector(
            "test",
            singletonList(helperClassName),
            emptyList(),
            this.getClass().getClassLoader(),
            ByteBuddyAgent.getInstrumentation());
    URLClassLoader bootstrapChild = new URLClassLoader(new URL[0], null);

    assertThatThrownBy(() -> bootstrapChild.loadClass(helperClassName))
        .isInstanceOf(ClassNotFoundException.class);

    ClassLoader bootstrapClassloader = null;
    injector.transform(null, null, bootstrapClassloader, null, null);
    Class<?> helperClass = bootstrapChild.loadClass(helperClassName);

    assertThat(helperClass.getClassLoader()).isEqualTo(bootstrapClassloader);
  }
}
