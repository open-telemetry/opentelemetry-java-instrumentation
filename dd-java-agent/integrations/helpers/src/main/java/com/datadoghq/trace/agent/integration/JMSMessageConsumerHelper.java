package com.datadoghq.trace.agent.integration;

import io.opentracing.contrib.jms.common.TracingMessageConsumer;
import javax.jms.MessageConsumer;
import org.jboss.byteman.rule.Rule;

public class JMSMessageConsumerHelper extends DDAgentTracingHelper<MessageConsumer> {

  public JMSMessageConsumerHelper(Rule rule) {
    super(rule);
  }

  @Override
  public MessageConsumer patch(MessageConsumer args) {
    return super.patch(args);
  }

  /**
   * Strategy: Wrapper the instance into a new one.
   *
   * @param consumer The JMS instance
   * @return A new instance with the old one wrapped
   * @throws Exception
   */
  protected MessageConsumer doPatch(MessageConsumer consumer) throws Exception {
    if (consumer instanceof TracingMessageConsumer) {
      return consumer;
    }
    return new TracingMessageConsumer(consumer, tracer);
  }
}
