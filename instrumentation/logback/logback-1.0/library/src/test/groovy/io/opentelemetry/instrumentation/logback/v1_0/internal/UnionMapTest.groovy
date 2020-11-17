/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.v1_0.internal

import spock.lang.Specification

class UnionMapTest extends Specification {

  def "maps"() {
    when:
    def union = new UnionMap(first, second)

    then:
    union['cat'] == 'meow'
    union['dog'] == 'bark'
    union['foo'] == 'bar'
    union['hello'] == 'world'
    union['giraffe'] == null

    !union.isEmpty()
    union.size() == 4
    union.containsKey('cat')
    union.containsKey('dog')
    union.containsKey('foo')
    union.containsKey('hello')
    !union.containsKey('giraffe')

    def set = union.entrySet()
    !set.isEmpty()
    set.size() == 4
    def copy = new ArrayList(set)
    copy.size() == 4

    where:
    first                      | second
    [cat: 'meow', dog: 'bark'] | [foo: 'bar', hello: 'world']
    // Overlapping entries in second does not affect the union.
    [cat: 'meow', dog: 'bark'] | [foo: 'bar', hello: 'world', cat: 'moo']
  }

  def "both empty"() {
    when:
    def union = new UnionMap(Collections.emptyMap(), Collections.emptyMap())

    then:
    union.isEmpty()
    union.size() == 0
    union['cat'] == null

    def set = union.entrySet()
    set.isEmpty()
    set.size() == 0
    def copy = new ArrayList(set)
    copy.size() == 0
  }

  def "one empty"() {
    when:
    def union = new UnionMap(first, second)

    then:
    !union.isEmpty()
    union.size() == 1
    union['cat'] == 'meow'
    union['dog'] == null

    def set = union.entrySet()
    !set.isEmpty()
    set.size() == 1
    def copy = new ArrayList(set)
    copy.size() == 1

    where:
    first                  | second
    [cat: 'meow']          | Collections.emptyMap()
    Collections.emptyMap() | [cat: 'meow']
  }
}
