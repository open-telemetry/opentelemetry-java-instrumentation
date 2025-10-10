/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.Phaser;
import org.junit.jupiter.api.Test;

class AgentClassLoaderTest {

  @Test
  void agentClassloaderDoesNotLockClassloadingAroundInstance() throws Exception {
    String className1 = "some/class/Name1";
    String className2 = "some/class/Name2";
    // any jar would do, use opentelemety sdk
    URL testJarLocation =
        OpenTelemetrySdk.class.getProtectionDomain().getCodeSource().getLocation();

    try (AgentClassLoader loader = new AgentClassLoader(new File(testJarLocation.toURI()))) {
      Phaser threadHoldLockPhase = new Phaser(2);
      Phaser acquireLockFromMainThreadPhase = new Phaser(2);

      // Use reflection to access protected getClassLoadingLock method
      Method getClassLoadingLockMethod =
          ClassLoader.class.getDeclaredMethod("getClassLoadingLock", String.class);
      getClassLoadingLockMethod.setAccessible(true);

      Thread thread1 =
          new Thread(
              () -> {
                try {
                  Object lock1 = getClassLoadingLockMethod.invoke(loader, className1);
                  synchronized (lock1) {
                    threadHoldLockPhase.arrive();
                    acquireLockFromMainThreadPhase.arriveAndAwaitAdvance();
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      thread1.start();

      Thread thread2 =
          new Thread(
              () -> {
                try {
                  threadHoldLockPhase.arriveAndAwaitAdvance();
                  Object lock2 = getClassLoadingLockMethod.invoke(loader, className2);
                  synchronized (lock2) {
                    acquireLockFromMainThreadPhase.arrive();
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      thread2.start();

      thread1.join();
      thread2.join();
      boolean applicationDidNotDeadlock = true;

      assertThat(applicationDidNotDeadlock).isTrue();
    }
  }

  @Test
  void multiReleaseJar() throws Exception {
    boolean jdk8 = "1.8".equals(System.getProperty("java.specification.version"));
    Class<?> mrJarClass =
        Class.forName("io.opentelemetry.instrumentation.resources.internal.ProcessArguments");
    // sdk is a multi release jar
    URL multiReleaseJar = mrJarClass.getProtectionDomain().getCodeSource().getLocation();

    try (AgentClassLoader loader =
        new AgentClassLoader(new File(multiReleaseJar.toURI())) {
          @Override
          protected String getClassSuffix() {
            return "";
          }
        }) {
      URL url =
          loader.findResource(
              "io/opentelemetry/instrumentation/resources/internal/ProcessArguments.class");

      assertThat(url).isNotNull();
      // versioned resource is found when not running on jdk 8
      assertThat(url.toString().contains("META-INF/versions/9/")).isNotEqualTo(jdk8);

      Class<?> clazz =
          loader.loadClass("io.opentelemetry.instrumentation.resources.internal.ProcessArguments");
      // class was loaded by agent loader used in this test
      assertThat(clazz.getClassLoader()).isEqualTo(loader);
      Method method = clazz.getDeclaredMethod("getProcessArguments");
      method.setAccessible(true);
      String[] result = (String[]) method.invoke(null);
      // jdk8 versions returns empty array, jdk9 version does not
      assertThat(result.length > 0).isNotEqualTo(jdk8);
    }
  }
}
