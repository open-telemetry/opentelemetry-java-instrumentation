package com.datadoghq.trace.writer

import spock.lang.Specification

class WriterQueueTest extends Specification {

  def "instantiate a empty queue throws an exception"() {
    when:
    new DDAgentWriter.WriterQueue<Integer>(0)

    then:
    thrown IllegalArgumentException

    when:
    new DDAgentWriter.WriterQueue<Integer>(-1)

    then:
    thrown IllegalArgumentException
  }

  def "full the queue without forcing"() {

    setup:
    def Q = new DDAgentWriter.WriterQueue<Integer>(capacity)
    def removed = false

    when:
    for (def i = 0; i < capacity; i++) {
      removed = removed || Q.add(i) != null
    }

    then:
    !removed

    where:
    capacity << [1, 10, 100]

  }

  def "force element add to a full queue"() {

    setup:
    def Q = new DDAgentWriter.WriterQueue<Integer>(capacity)
    for (def i = 0; i < capacity; i++) {
      Q.add(i)
    }

    when:
    def removed = Q.add(1)

    then:
    removed != null
    Q.size() == capacity

    where:
    capacity << [1, 10, 100]

  }

  def "drain the queue into another collection"() {

    setup:
    def Q = new DDAgentWriter.WriterQueue<Integer>(capacity)
    def L = []
    for (def i = 0; i < capacity; i++) {
      Q.add(i)
    }

    when:
    def nb = Q.drainTo(L)

    then:
    nb == L.size()
    nb == capacity
    Q.isEmpty()
    Q.size() == 0

    where:
    capacity << [1, 10, 100]

  }

  def "Queue should be never locked"() {

    setup:
    def Q = new DDAgentWriter.WriterQueue<Integer>(1)
    def L = Collections.emptyList() // raise an error if you add an element
    Q.add(42)

    when:
    Q.drainTo(L)

    then:
    thrown Exception

    when:
    // still able to add element
    def removed = Q.add(1337)

    then:
    removed == 42
    Q.size() == 1


  }

//  def "Multi threading test"() {
//    setup:
//    def Q = new DDAgentWriter.WriterQueue<Integer>(10)
//    def start = new CountDownLatch(5)
//    def executor = Executors.newFixedThreadPool(5)
//    def end = false
//    def L = []
//
//    def pushTask = new Runnable() {
//      @Override
//      void run() {
//        start.await()
//        while (!end) Q.add(1)
//      }
//    }
//
//    def popTask = new Runnable() {
//      @Override
//      void run() {
//        start.await()
//        def nbDrains = 0
//        while (!end) {
//          Q.drainTo(L)
//          sleep(10)
//          end = ++nbDrains == 1000
//        }
//      }
//    }
//
//    when:
//    // 4 pushers
//    executor.submit(pushTask)
//    executor.submit(pushTask)
//    executor.submit(pushTask)
//    executor.submit(pushTask)
//    // 1 popper (do 1000 drains)
//    // executor.submit(popTask)
//
//
//    then:
//    // to be here
//    Q.size() == 10 || L.size() == 10 || Q.size() + L.size() == 10
//
//  }

}
