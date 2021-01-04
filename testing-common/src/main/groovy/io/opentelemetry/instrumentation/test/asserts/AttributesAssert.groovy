/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.asserts

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import java.util.regex.Pattern

class AttributesAssert {
  private final Map<String, Object> attributes
  private final Set<String> assertedAttributes = new TreeSet<>()

  private AttributesAssert(Map<String, Object> attributes) {
    this.attributes = attributes
  }

  static void assertAttributes(Map<String, Object> attributes,
                               @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.AttributesAssert'])
                               @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new AttributesAssert(attributes)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertAttributesAllVerified()
  }

  def attribute(String name, value) {
    if (value == null) {
      return
    }
    assertedAttributes.add(name)
    def val = attributes.get(name)
    if (value instanceof Pattern) {
      assert val =~ value
    } else if (value instanceof Class) {
      assert ((Class) value).isInstance(val)
    } else if (value instanceof Closure) {
      assert ((Closure) value).call(val)
    } else {
      assert val == value
    }
  }

  def attribute(String name) {
    return attributes[name]
  }

  def methodMissing(String name, args) {
    if (args.length == 0) {
      throw new IllegalArgumentException(args.toString())
    }
    attribute(name, args[0])
  }

  void assertAttributesAllVerified() {
    Set<String> allAttributes = new TreeSet<>(attributes.keySet())
    Set<String> unverifiedAttributes = new TreeSet(allAttributes)
    unverifiedAttributes.removeAll(assertedAttributes)

    // Don't need to verify thread details.
    assertedAttributes.add("thread.id")
    unverifiedAttributes.remove("thread.id")
    assertedAttributes.add("thread.name")
    unverifiedAttributes.remove("thread.name")

    // The first and second condition in the assert are exactly the same
    // but both are included in order to provide better context in the error message.
    // containsAll because tests may assert more attributes than span actually has
    assert unverifiedAttributes.isEmpty() && assertedAttributes.containsAll(allAttributes)
  }
}
