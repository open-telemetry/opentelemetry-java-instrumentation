package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.api.trace.Span;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import java.util.List;

public class TracingMessageInterceptor {

  public void producerIntercept(String addr, Message msg) {
    Span span = RocketMqProducerTracer.tracer().startProducerSpan(addr, msg);
    SendResult sendResult = new SendResult();
    sendResult.setSendStatus(SendStatus.SEND_OK);
    RocketMqProducerTracer.tracer().onCallback(span, sendResult);
    RocketMqProducerTracer.tracer().end(span);
  }

  public void consumerConcurrentlyIntercept(List<MessageExt> msgs) {
    Span span =  RocketMqConsumerTracer.tracer().startSpan(msgs);
    RocketMqConsumerTracer.tracer().endConcurrentlySpan(span, ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
    RocketMqConsumerTracer.tracer().tracer().end(span);
  }

  public void consumerOrderlyIntercept(List<MessageExt> msgs) {
    Span span =  RocketMqConsumerTracer.tracer().startSpan(msgs);
    RocketMqConsumerTracer.tracer().endOrderlySpan(span, ConsumeOrderlyStatus.SUCCESS);
    RocketMqConsumerTracer.tracer().tracer().end(span);
  }
}
