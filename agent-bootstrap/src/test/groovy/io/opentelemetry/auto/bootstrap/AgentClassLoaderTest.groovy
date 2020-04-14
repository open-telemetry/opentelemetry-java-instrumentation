/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.bootstrap

import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit

class AgentClassLoaderTest extends Specification {
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def "agent classloader does not lock classloading around instance"() {
    setup:
    def className1 = 'some/class/Name1'
    def className2 = 'some/class/Name2'
    final URL loc = getClass().getProtectionDomain().getCodeSource().getLocation()
    final AgentClassLoader loader = new AgentClassLoader(loc, null, null)
    final Phaser threadHoldLockPhase = new Phaser(2)
    final Phaser acquireLockFromMainThreadPhase = new Phaser(2)

    when:
    final Thread thread1 = new Thread() {
      @Override
      void run() {
        synchronized (loader.getClassLoadingLock(className1)) {
          threadHoldLockPhase.arrive()
          acquireLockFromMainThreadPhase.arriveAndAwaitAdvance()
        }
      }
    }
    thread1.start()

    final Thread thread2 = new Thread() {
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
