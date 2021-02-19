/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap

import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import spock.lang.Specification
import spock.lang.Timeout

class AgentClassLoaderTest extends Specification {
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def "agent classloader does not lock classloading around instance"() {
    setup:
    def className1 = 'some/class/Name1'
    def className2 = 'some/class/Name2'
    AgentClassLoader loader = new AgentClassLoader(null, null, null)
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
}
