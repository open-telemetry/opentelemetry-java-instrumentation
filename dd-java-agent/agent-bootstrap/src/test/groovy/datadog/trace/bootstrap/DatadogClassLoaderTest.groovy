package datadog.trace.bootstrap

import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit

class DatadogClassLoaderTest extends Specification {
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def "DD classloader does not lock classloading around instance"() {
    setup:
    def className1 = 'some/class/Name1'
    def className2 = 'some/class/Name2'
    final URL loc = getClass().getProtectionDomain().getCodeSource().getLocation()
    final DatadogClassLoader ddLoader = new DatadogClassLoader(loc, loc, (ClassLoader) null)
    final Phaser threadHoldLockPhase = new Phaser(2)
    final Phaser acquireLockFromMainThreadPhase = new Phaser(2)

    when:
    final Thread thread1 = new Thread() {
      @Override
      void run() {
        synchronized (ddLoader.getClassLoadingLock(className1)) {
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
        synchronized (ddLoader.getClassLoadingLock(className2)) {
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
