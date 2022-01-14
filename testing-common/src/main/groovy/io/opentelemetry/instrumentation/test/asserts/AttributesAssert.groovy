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

  def attribute(String name, expected) {
    if (expected == null) {
      return
    }
    assertedAttributes.add(name)
    def value = attributes.get(name)
    if (expected instanceof Pattern) {
      assert value =~ expected, "value '$value' does not match regex '$expected'"
    } else if (expected instanceof Class) {
      assert ((Class) expected).isInstance(value), "value '$value' is not an instance of $expected.name"
    } else if (expected instanceof Closure) {
      assert ((Closure) expected).call(value), "value '$value' fails the passed predicate"
    } else {
      assert value == expected
    }
  }

  def methodMissing(String name, args) {
    if (args.length == 0) {
      throw new IllegalArgumentException(args.toString())
    }
    attribute(name, args[0])
  }

  // this could be private, but then codenarc fails, thinking (incorrectly) that it's unused
  void assertAttributesAllVerified() {
    Set<String> allAttributes = new TreeSet<>(attributes.keySet())
    Set<String> unverifiedAttributes = new TreeSet(allAttributes)
    unverifiedAttributes.removeAll(assertedAttributes)

    // The first and second condition in the assert are exactly the same
    // but both are included in order to provide better context in the error message.
    // containsAll because tests may assert more attributes than span actually has
    assert unverifiedAttributes.isEmpty() && assertedAttributes.containsAll(allAttributes)
  }
}
