package datadog.trace.api.sampling

import datadog.opentracing.DDSpan
import datadog.opentracing.SpanFactory
import datadog.trace.common.sampling.PrioritySampler
import datadog.trace.common.sampling.RateByServiceSampler
import datadog.trace.common.sampling.RuleBasedSampler
import datadog.trace.common.sampling.Sampler
import datadog.trace.util.test.DDSpecification

import static datadog.trace.api.Config.TRACE_SAMPLING_DEFAULT_RATE
import static datadog.trace.api.Config.TRACE_SAMPLING_OPERATION_RULES
import static datadog.trace.api.Config.TRACE_SAMPLING_RATE_LIMIT
import static datadog.trace.api.Config.TRACE_SAMPLING_SERVICE_RULES
import static datadog.trace.api.sampling.PrioritySampling.SAMPLER_DROP
import static datadog.trace.api.sampling.PrioritySampling.SAMPLER_KEEP

class RuleBasedSamplingTest extends DDSpecification {
  def "Rule Based Sampler is not created when properties not set"() {
    when:
    Sampler sampler = Sampler.Builder.forConfig(new Properties())

    then:
    !(sampler instanceof RuleBasedSampler)
  }

  def "Rule Based Sampler is not created when just rate limit set"() {
    when:
    Properties properties = new Properties()
    properties.setProperty(TRACE_SAMPLING_RATE_LIMIT, "50")
    Sampler sampler = Sampler.Builder.forConfig(properties)

    then:
    !(sampler instanceof RuleBasedSampler)
  }

  def "sampling config combinations"() {
    given:
    Properties properties = new Properties()
    if (serviceRules != null) {
      properties.setProperty(TRACE_SAMPLING_SERVICE_RULES, serviceRules)
    }

    if (operationRules != null) {
      properties.setProperty(TRACE_SAMPLING_OPERATION_RULES, operationRules)
    }

    if (defaultRate != null) {
      properties.setProperty(TRACE_SAMPLING_DEFAULT_RATE, defaultRate)
    }

    if (rateLimit != null) {
      properties.setProperty(TRACE_SAMPLING_RATE_LIMIT, rateLimit)
    }

    when:
    Sampler sampler = Sampler.Builder.forConfig(properties)

    then:
    sampler instanceof PrioritySampler

    when:
    DDSpan span = SpanFactory.newSpanOf("service", "bar")
    span.setOperationName("operation")
    ((PrioritySampler) sampler).setSamplingPriority(span)

    then:
    span.getMetrics().get(RuleBasedSampler.SAMPLING_RULE_RATE) == expectedRuleRate
    span.getMetrics().get(RuleBasedSampler.SAMPLING_LIMIT_RATE) == expectedRateLimit
    span.getMetrics().get(RateByServiceSampler.SAMPLING_AGENT_RATE) == expectedAgentRate
    span.getSamplingPriority() == expectedPriority

    where:
    serviceRules      | operationRules      | defaultRate | rateLimit | expectedRuleRate | expectedRateLimit | expectedAgentRate | expectedPriority
    // Matching neither passes through to rate based sampler
    "xx:1"            | null                | null        | "50"      | null             | null              | 1.0               | SAMPLER_KEEP
    null              | "xx:1"              | null        | "50"      | null             | null              | 1.0               | SAMPLER_KEEP

    // Matching neither with default rate
    null              | null                | "1"         | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    null              | null                | "0"         | "50"      | 0                | null              | null              | SAMPLER_DROP
    "xx:1"            | null                | "1"         | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    null              | "xx:1"              | "1"         | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    "xx:1"            | null                | "0"         | "50"      | 0                | null              | null              | SAMPLER_DROP
    null              | "xx:1"              | "0"         | "50"      | 0                | null              | null              | SAMPLER_DROP

    // Matching service: keep
    "service:1"       | null                | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    "s.*:1"           | null                | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    ".*e:1"           | null                | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    "[a-z]+:1"        | null                | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP

    // Matching service: drop
    "service:0"       | null                | null        | "50"      | 0                | null              | null              | SAMPLER_DROP
    "s.*:0"           | null                | null        | "50"      | 0                | null              | null              | SAMPLER_DROP
    ".*e:0"           | null                | null        | "50"      | 0                | null              | null              | SAMPLER_DROP
    "[a-z]+:0"        | null                | null        | "50"      | 0                | null              | null              | SAMPLER_DROP

    // Matching service overrides default rate
    "service:1"       | null                | "0"         | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    "service:0"       | null                | "1"         | "50"      | 0                | null              | null              | SAMPLER_DROP

    // multiple services
    "xxx:0,service:1" | null                | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    "xxx:1,service:0" | null                | null        | "50"      | 0                | null              | null              | SAMPLER_DROP

    // Matching operation : keep
    null              | "operation:1"       | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    null              | "o.*:1"             | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    null              | ".*n:1"             | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    null              | "[a-z]+:1"          | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP

    // Matching operation: drop
    null              | "operation:0"       | null        | "50"      | 0                | null              | null              | SAMPLER_DROP
    null              | "o.*:0"             | null        | "50"      | 0                | null              | null              | SAMPLER_DROP
    null              | ".*n:0"             | null        | "50"      | 0                | null              | null              | SAMPLER_DROP
    null              | "[a-z]+:0"          | null        | "50"      | 0                | null              | null              | SAMPLER_DROP

    // Matching operation overrides default rate
    null              | "operation:1"       | "0"         | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    null              | "operation:0"       | "1"         | "50"      | 0                | null              | null              | SAMPLER_DROP

    // multiple operation combinations
    null              | "xxx:0,operation:1" | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    null              | "xxx:1,operation:0" | null        | "50"      | 0                | null              | null              | SAMPLER_DROP

    // Service and operation name combinations
    "service:1"       | "operation:0"       | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    "service:1"       | "xxx:0"             | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    "service:0"       | "operation:1"       | null        | "50"      | 0                | null              | null              | SAMPLER_DROP
    "service:0"       | "xxx:1"             | null        | "50"      | 0                | null              | null              | SAMPLER_DROP
    "xxx:0"           | "operation:1"       | null        | "50"      | 1.0              | 50                | null              | SAMPLER_KEEP
    "xxx:1"           | "operation:0"       | null        | "50"      | 0                | null              | null              | SAMPLER_DROP

    // There are no tests for ordering within service or operation rules because the rule order in that case is unspecified
  }

  def "Rate limit is set for rate limited spans"() {
    when:
    Properties properties = new Properties()
    properties.setProperty(TRACE_SAMPLING_SERVICE_RULES, "service:1")
    properties.setProperty(TRACE_SAMPLING_RATE_LIMIT, "1")
    Sampler sampler = Sampler.Builder.forConfig(properties)

    DDSpan span1 = SpanFactory.newSpanOf("service", "bar")
    DDSpan span2 = SpanFactory.newSpanOf("service", "bar")

    ((PrioritySampler) sampler).setSamplingPriority(span1)
    // Span 2 should be rate limited if there isn't a >1 sec delay between these 2 lines
    ((PrioritySampler) sampler).setSamplingPriority(span2)

    then:
    span1.getMetrics().get(RuleBasedSampler.SAMPLING_RULE_RATE) == 1.0
    span1.getMetrics().get(RuleBasedSampler.SAMPLING_LIMIT_RATE) == 1.0
    span1.getMetrics().get(RateByServiceSampler.SAMPLING_AGENT_RATE) == null
    span1.getSamplingPriority() == SAMPLER_KEEP

    span2.getMetrics().get(RuleBasedSampler.SAMPLING_RULE_RATE) == 1.0
    span2.getMetrics().get(RuleBasedSampler.SAMPLING_LIMIT_RATE) == 1.0
    span2.getMetrics().get(RateByServiceSampler.SAMPLING_AGENT_RATE) == null
    span2.getSamplingPriority() == SAMPLER_DROP
  }

  def "Rate limit is set for rate limited spans (matched on different rules)"() {
    when:
    Properties properties = new Properties()
    properties.setProperty(TRACE_SAMPLING_SERVICE_RULES, "service:1,foo:1")
    properties.setProperty(TRACE_SAMPLING_RATE_LIMIT, "1")
    Sampler sampler = Sampler.Builder.forConfig(properties)

    DDSpan span1 = SpanFactory.newSpanOf("service", "bar")
    DDSpan span2 = SpanFactory.newSpanOf("foo", "bar")

    ((PrioritySampler) sampler).setSamplingPriority(span1)
    // Span 2 should be rate limited if there isn't a >1 sec delay between these 2 lines
    ((PrioritySampler) sampler).setSamplingPriority(span2)

    then:
    span1.getMetrics().get(RuleBasedSampler.SAMPLING_RULE_RATE) == 1.0
    span1.getMetrics().get(RuleBasedSampler.SAMPLING_LIMIT_RATE) == 1.0
    span1.getMetrics().get(RateByServiceSampler.SAMPLING_AGENT_RATE) == null
    span1.getSamplingPriority() == SAMPLER_KEEP

    span2.getMetrics().get(RuleBasedSampler.SAMPLING_RULE_RATE) == 1.0
    span2.getMetrics().get(RuleBasedSampler.SAMPLING_LIMIT_RATE) == 1.0
    span2.getMetrics().get(RateByServiceSampler.SAMPLING_AGENT_RATE) == null
    span2.getSamplingPriority() == SAMPLER_DROP
  }
}
