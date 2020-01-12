package io.opentelemetry.auto.test.asserts

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.sdk.trace.SpanData
import io.opentelemetry.trace.AttributeValue

import java.util.regex.Pattern

class TagsAssert {
  private final Map<String, AttributeValue> tags
  private final Set<String> assertedTags = new TreeSet<>()

  private TagsAssert(SpanData span) {
    this.tags = span.attributes
  }

  static void assertTags(SpanData span,
                         @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.TagsAssert'])
                         @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new TagsAssert(span)
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
    def val = getVal(tags[name])
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
    def set = new TreeMap<>(tags).keySet()
    set.removeAll(assertedTags)
    // The primary goal is to ensure the set is empty.
    // tags and assertedTags are included via an "always true" comparison
    // so they provide better context in the error message.
    assert (tags.entrySet() != assertedTags || assertedTags.isEmpty()) && set.isEmpty()
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
