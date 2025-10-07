/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.test;

import static io.opentelemetry.instrumentation.test.utils.ClasspathUtils.isClassLoaded;
import static io.opentelemetry.instrumentation.test.utils.GcUtils.awaitGc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.javaagent.tooling.AgentInstaller;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import org.junit.jupiter.api.Test;

class HelperInjectionTest {

  @Test
  void helpersInjectedToNonDelegatingClassloader() throws Exception {
    URL[] helpersSourceUrls = new URL[1];
    helpersSourceUrls[0] = HelperClass.class.getProtectionDomain().getCodeSource().getLocation();
    ClassLoader helpersSourceLoader = new URLClassLoader(helpersSourceUrls);

    String helperClassName = HelperInjectionTest.class.getPackage().getName() + ".HelperClass";
    HelperInjector injector = new HelperInjector("test", List.of(helperClassName), List.of(), helpersSourceLoader, null);
    AtomicReference<URLClassLoader> emptyLoader = new AtomicReference<>(new URLClassLoader(new URL[0], null));

    assertThatThrownBy(() -> emptyLoader.get().loadClass(helperClassName))
        .isInstanceOf(ClassNotFoundException.class);

    injector.transform(null, null, emptyLoader.get(), null, null);
    HelperInjector.loadHelperClass(emptyLoader.get(), helperClassName);
    emptyLoader.get().loadClass(helperClassName);

    assertThat(isClassLoaded(helperClassName, emptyLoader.get())).isTrue();
    // injecting into emptyLoader should not cause helper class to be load in the helper source classloader
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
        ByteBuddyAgent.getInstrumentation(),
        this.getClass().getClassLoader(),
        EarlyInitAgentConfig.create());

    String helperClassName = HelperInjectionTest.class.getPackage().getName() + ".HelperClass";
    HelperInjector injector = new HelperInjector(
        "test",
        List.of(helperClassName),
        List.of(),
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

  @Test
  void checkHardReferencesOnClassInjection() throws Exception {
    String helperClassName = HelperInjectionTest.class.getPackage().getName() + ".HelperClass";

    // Copied from HelperInjector:
    ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(Utils.getAgentClassLoader());
    byte[] classBytes = locator.locate(helperClassName).resolve();
    TypeDescription typeDesc = new TypeDescription.Latent(
        helperClassName, 0, null, Collections.emptyList());

    AtomicReference<URLClassLoader> emptyLoader = new AtomicReference<>(new URLClassLoader(new URL[0], null));
    AtomicReference<ClassInjector> injector = new AtomicReference<>(new ClassInjector.UsingReflection(emptyLoader.get()));
    injector.get().inject(Collections.singletonMap(typeDesc, classBytes));

    WeakReference<ClassInjector> injectorRef = new WeakReference<>(injector.get());
    injector.set(null);

    awaitGc(injectorRef, Duration.ofSeconds(10));

    assertThat(injectorRef.get()).isNull();

    WeakReference<URLClassLoader> loaderRef = new WeakReference<>(emptyLoader.get());
    emptyLoader.set(null);

    awaitGc(loaderRef, Duration.ofSeconds(10));

    assertThat(loaderRef.get()).isNull();
  }
}
