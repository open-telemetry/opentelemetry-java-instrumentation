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

package io.opentelemetry.auto.test.asserts

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.common.AttributeValue

import java.util.regex.Pattern

class AttributesAssert {
  private final Map<String, AttributeValue> attributes
  private final Set<String> assertedAttributes = new TreeSet<>()

  private AttributesAssert(attributes) {
    this.attributes = attributes
  }

  static void assertAttributes(Map<String, AttributeValue> attributes,
                               @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.AttributesAssert'])
                         @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new AttributesAssert(attributes)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertAttributesAllVerified()
  }

  def errorAttributes(Class<Throwable> errorType) {
    errorAttributes(errorType, null)
  }

  def errorAttributes(Class<Throwable> errorType, message) {
    attribute("error.type", errorType.name)
    attribute("error.stack", String)

    if (message != null) {
      attribute("error.msg", message)
    }
  }

  def attribute(String name, value) {
    if (value == null) {
      return
    }
    assertedAttributes.add(name)
    def val = getVal(attributes[name])
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
    def set = new TreeMap<>(attributes).keySet()
    set.removeAll(assertedAttributes)
    // The primary goal is to ensure the set is empty.
    // attributes and assertedAttributes are included via an "always true" comparison
    // so they provide better context in the error message.
    assert (attributes.entrySet() != assertedAttributes || assertedAttributes.isEmpty()) && set.isEmpty()
  }

  private static Object getVal(AttributeValue attributeValue) {
    if (attributeValue == null) {
      return null
    }
    switch (attributeValue.type) {
      case AttributeValue.Type.STRING:
        return attributeValue.stringValue
      case AttributeValue.Type.BOOLEAN:
        return attributeValue.booleanValue
      case AttributeValue.Type.LONG:
        return attributeValue.longValue
      case AttributeValue.Type.DOUBLE:
        return attributeValue.doubleValue
      default:
        throw new IllegalStateException("Unexpected type: " + attributeValue.type)
    }
  }
}
