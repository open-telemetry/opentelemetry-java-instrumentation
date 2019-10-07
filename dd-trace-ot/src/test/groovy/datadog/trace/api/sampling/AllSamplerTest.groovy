package datadog.trace.api.sampling

import datadog.opentracing.DDSpan
import datadog.trace.common.sampling.AllSampler
import datadog.trace.util.test.DDSpecification
import spock.lang.Subject

import java.util.regex.Pattern

class AllSamplerTest extends DDSpecification {

  @Subject
  DDSpan span = Mock()

  private final AllSampler sampler = new AllSampler()

  def "test AllSampler"() {
    expect:
    for (int i = 0; i < 500; i++) {
      assert sampler.doSample(span)
    }
  }

  def "test skip tag sampler"() {
    setup:
    final Map<String, Object> tags = new HashMap<>()
    2 * span.getTags() >> tags
    sampler.addSkipTagPattern("http.url", Pattern.compile(".*/hello"))

    when:
    tags.put("http.url", "http://a/hello")

    then:
    !sampler.sample(span)

    when:
    tags.put("http.url", "http://a/hello2")

    then:
    sampler.sample(span)
  }
}
