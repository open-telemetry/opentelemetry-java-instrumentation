/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap

import io.opentelemetry.sdk.OpenTelemetrySdk
import spock.lang.Specification

import java.lang.reflect.Method
import java.util.concurrent.Phaser

class AgentClassLoaderTest extends Specification {

  def "agent classloader does not lock classloading around instance"() {
    setup:
    def className1 = 'some/class/Name1'
    def className2 = 'some/class/Name2'
    // any jar would do, use opentelemety sdk
    URL testJarLocation = OpenTelemetrySdk.getProtectionDomain().getCodeSource().getLocation()
    AgentClassLoader loader = new AgentClassLoader(new File(testJarLocation.toURI()))
    Phaser threadHoldLockPhase = new Phaser(2)
    Phaser acquireLockFromMainThreadPhase = new Phaser(2)

    when:
    Thread thread1 = new Thread() {
      @Override
      void run() {
        synchronized (loader.getClassLoadingLock(className1)) {
          threadHoldLockPhase.arrive()
          acquireLockFromMainThreadPhase.arriveAndAwaitAdvance()
        }
      }
    }
    thread1.start()

    Thread thread2 = new Thread() {
      @Override
      void run() {
        threadHoldLockPhase.arriveAndAwaitAdvance()
        synchronized (loader.getClassLoadingLock(className2)) {
          acquireLockFromMainThreadPhase.arrive()
        }
      }
    }
    thread2.start()
    thread1.join()
    thread2.join()
    boolean applicationDidNotDeadlock = true

    then:
    applicationDidNotDeadlock
  }

  def "multi release jar"() {
    setup:
    boolean jdk8 = "1.8" == System.getProperty("java.specification.version")
    def mrJarClass = Class.forName("io.opentelemetry.instrumentation.resources.ProcessArguments")
    // sdk is a multi release jar
    URL multiReleaseJar = mrJarClass.getProtectionDomain().getCodeSource().getLocation()
    AgentClassLoader loader = new AgentClassLoader(new File(multiReleaseJar.toURI())) {
      @Override
      protected String getClassSuffix() {
        return ""
      }
    }

    when:
    URL url = loader.findResource("io/opentelemetry/instrumentation/resources/ProcessArguments.class")

    then:
    url != null
    // versioned resource is found when not running on jdk 8
    jdk8 != url.toString().contains("META-INF/versions/9/")

    and:
    Class<?> clazz = loader.loadClass("io.opentelemetry.instrumentation.resources.ProcessArguments")
    // class was loaded by agent loader used in this test
    clazz.getClassLoader() == loader
    Method method = clazz.getDeclaredMethod("getProcessArguments")
    method.setAccessible(true)
    String[] result = method.invoke(null)
    // jdk8 versions returns empty array, jdk9 version does not
    jdk8 != result.length > 0
  }
}
