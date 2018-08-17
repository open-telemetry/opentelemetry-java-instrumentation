package datadog.trace.bootstrap

import spock.lang.Specification

import java.util.concurrent.Phaser

class DatadogClassLoaderTest extends Specification {
  def "DD classloader does not lock classloading around instance" () {
    setup:
    def className1 = 'some/class/Name1'
    def className2 = 'some/class/Name2'
    final URL loc = getClass().getProtectionDomain().getCodeSource().getLocation()
    final DatadogClassLoader ddLoader = new DatadogClassLoader(loc, loc, (ClassLoader) null)
    final Phaser threadHoldLockPhase = new Phaser(2)
    final Phaser acquireLockFromMainThreadPhase = new Phaser(2)

    when:
    final Thread thread = new Thread() {
      @Override
      void run() {
        synchronized (ddLoader.getClassLoadingLock(className1)) {
          threadHoldLockPhase.arrive()
          acquireLockFromMainThreadPhase.arriveAndAwaitAdvance()
        }
      }
    }
    thread.start()

    threadHoldLockPhase.arriveAndAwaitAdvance()
    synchronized (ddLoader.getClassLoadingLock(className2)) {
      acquireLockFromMainThreadPhase.arrive()
    }
    thread.join()
    boolean applicationDidNotDeadlock = true

    then:
    applicationDidNotDeadlock
  }
}
