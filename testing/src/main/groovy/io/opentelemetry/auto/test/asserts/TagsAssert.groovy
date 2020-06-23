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
import io.opentelemetry.common.ReadableAttributes
import io.opentelemetry.common.ReadableKeyValuePairs
import java.util.regex.Pattern

class TagsAssert {
  private final ReadableAttributes tags
  private final Set<String> assertedTags = new TreeSet<>()

  private TagsAssert(ReadableAttributes attributes) {
    this.tags = attributes
  }

  static void assertTags(ReadableAttributes attributes,
                         @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.TagsAssert'])
                         @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new TagsAssert(attributes)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertTagsAllVerified()
  }

  def errorTags(Class<Throwable> errorType) {
    errorTags(errorType, null)
  }

  def errorTags(Class<Throwable> errorType, message) {
    tag("error.type", errorType.name)
    tag("error.stack", String)

    if (message != null) {
      tag("error.msg", message)
    }
  }

  def tag(String name, value) {
    if (value == null) {
      return
    }
    assertedTags.add(name)
    def val = getVal(tags.get(name))
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

  def tag(String name) {
    return tags[name]
  }

  def methodMissing(String name, args) {
    if (args.length == 0) {
      throw new IllegalArgumentException(args.toString())
    }
    tag(name, args[0])
  }

  void assertTagsAllVerified() {
    Set<String> allTags = new TreeSet<>()
    tags.forEach(new ReadableKeyValuePairs.KeyValueConsumer<AttributeValue>() {
      @Override
      void consume(String key, AttributeValue value) {
        allTags.add(key)
      }
    })
    Set<String> unverifiedTags = new TreeSet(allTags)
    unverifiedTags.removeAll(assertedTags)
    // tags and assertedTags are included to provide better context in the error message.
    //containsAll because we may assert more than span actually has
    assert unverifiedTags.isEmpty() && assertedTags.containsAll(allTags)
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
