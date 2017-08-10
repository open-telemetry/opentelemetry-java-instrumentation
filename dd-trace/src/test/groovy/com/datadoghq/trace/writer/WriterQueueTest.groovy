package com.datadoghq.trace.writer

import spock.lang.Specification

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

}
