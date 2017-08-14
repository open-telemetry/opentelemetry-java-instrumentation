package com.datadoghq.trace.writer

import spock.lang.Specification

import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger

class WriterQueueTest extends Specification {

  def "instantiate a empty queue throws an exception"() {
    when:
    new WriterQueue<Integer>(0)

    then:
    thrown IllegalArgumentException

    when:
    new WriterQueue<Integer>(-1)

    then:
    thrown IllegalArgumentException
  }

  def "full the queue without forcing"() {

    setup:
    def queue = new WriterQueue<Integer>(capacity)
    def removed = false

    when:
    for (def i = 0; i < capacity; i++) {
      removed = removed || queue.add(i) != null
    }

    then:
    !removed

    where:
    capacity << [1, 10, 100]

  }

  def "force element add to a full queue"() {

    setup:
    def queue = new WriterQueue<Integer>(capacity)
    for (def i = 0; i < capacity; i++) {
      queue.add(i)
    }

    when:
    def removed = queue.add(1)

    then:
    removed != null
    queue.size() == capacity

    where:
    capacity << [1, 10, 100]

  }

  def "drain the queue into another collection"() {

    setup:
    def queue = new WriterQueue<Integer>(capacity)
    for (def i = 0; i < capacity; i++) {
      queue.add(i)
    }

    when:
    def list = queue.getAll()

    then:
    list.size() == capacity
    queue.isEmpty()
    queue.size() == 0

    where:
    capacity << [1, 10, 100]

  }

  def "check concurrency on writes"() {
    setup:

    def phaser_1 = new Phaser()
    def phaser_2 = new Phaser()
    def queue = new WriterQueue<Integer>(capacity)
    def insertionCount = new AtomicInteger(0)

    phaser_1.register() // global start
    phaser_2.register() // global stop

    numberThreads.times {
      phaser_1.register()
      Thread.start {
        phaser_2.register()
        phaser_1.arriveAndAwaitAdvance()
        numberInsertionsPerThread.times {
          queue.add(1)
          insertionCount.getAndIncrement()
        }
        phaser_2.arriveAndAwaitAdvance()
      }
    }

    when:
    phaser_1.arriveAndAwaitAdvance() // allow threads to start
    phaser_2.arriveAndAwaitAdvance() // wait till the job is not finished

    then:
    queue.size() == capacity
    insertionCount.get() == numberInsertionsPerThread * numberThreads

    where:
    capacity = 100
    numberThreads << [1, 10, 100]
    numberInsertionsPerThread = 100

  }


  def "check concurrency on writes and reads"() {
    setup:
    def phaser_1 = new Phaser()
    def phaser_2 = new Phaser()
    def queue = new WriterQueue<Integer>(capacity)
    def insertionCount = new AtomicInteger(0)
    def droppedCount = new AtomicInteger(0)
    def getCount = new AtomicInteger(0)
    def numberElements = new AtomicInteger(0)

    phaser_1.register() // global start
    phaser_2.register() // global stop

    // writes
    numberThreadsWrites.times {
      phaser_1.register()
      Thread.start {
        phaser_2.register()
        phaser_1.arriveAndAwaitAdvance()
        numberInsertionsPerThread.times {
          queue.add(1) != null ? droppedCount.getAndIncrement() : null
          insertionCount.getAndIncrement()
        }
        phaser_2.arriveAndAwaitAdvance()
      }
    }

    // reads
    numberThreadsReads.times {
      phaser_1.register()
      Thread.start {
        phaser_2.register()
        phaser_1.arriveAndAwaitAdvance()
        numberGetsPerThread.times {
          numberElements.getAndAdd(queue.getAll().size())
          getCount.getAndIncrement()
        }
        phaser_2.arriveAndAwaitAdvance()
      }
    }

    when:
    phaser_1.arriveAndAwaitAdvance() // allow threads to start
    phaser_2.arriveAndAwaitAdvance() // wait till the job is not finished

    then:
    insertionCount.get() == numberInsertionsPerThread * numberThreadsWrites
    getCount.get() == numberGetsPerThread * numberThreadsReads
    insertionCount.get() == numberElements + queue.size() + droppedCount

    where:
    capacity = 100
    numberThreadsWrites << [1, 10, 100]
    numberThreadsReads << [1, 5, 10]
    numberInsertionsPerThread = 100
    numberGetsPerThread = 5

  }

}
