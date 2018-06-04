package datadog.trace.agent.test

import datadog.opentracing.DDSpan

class TagsAssert {
  private final Map<String, Object> tags
  private final Set<String> assertedTags = new TreeSet<>()

  private TagsAssert(DDSpan span) {
    this.tags = new TreeMap(span.tags)
  }

  static void assertTags(DDSpan span,
                         @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new TagsAssert(span)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertTracesAllVerified()
    asserter
  }

  def defaultTags() {
    assertedTags.add("thread.name")
    assertedTags.add("thread.id")

    assert tags["thread.name"] != null
    assert tags["thread.id"] != null
  }

  def errorTags(Class<Throwable> errorType) {
    errorTags(errorType, null)
  }

  def errorTags(Class<Throwable> errorType, Object message) {
    methodMissing("error", [true].toArray())
    methodMissing("error.type", [errorType.name].toArray())
    methodMissing("error.stack", [String].toArray())

    if (message != null) {
      methodMissing("error.msg", [message].toArray())
    } else {
      // don't make the message check mandatory, in case of exception messages that change on every run,
      // i.e. random port that is destined to fail every time
      assertedTags.add("error.msg")
    }
  }

  def methodMissing(String name, args) {
    if (args.length > 1) {
      throw new IllegalArgumentException(args.toString())
    }
    assertedTags.add(name)
    def arg = args[0]
    if (arg instanceof Class) {
      assert ((Class) arg).isInstance(tags[name])
    } else {
      assert tags[name] == arg
    }
  }

  void assertTracesAllVerified() {
    assert tags.keySet() == assertedTags
  }
}
