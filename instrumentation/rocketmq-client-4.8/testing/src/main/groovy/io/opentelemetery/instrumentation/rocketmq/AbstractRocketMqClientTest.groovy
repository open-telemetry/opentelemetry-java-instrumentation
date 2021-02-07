package io.opentelemetery.instrumentation.rocketmq

import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly
import org.apache.rocketmq.client.producer.DefaultMQProducer
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.client.producer.SendStatus
import org.apache.rocketmq.common.message.Message
import org.apache.rocketmq.common.message.MessageExt
import org.apache.rocketmq.remoting.common.RemotingHelper
import spock.lang.Shared
import spock.lang.Unroll
import static io.opentelemetry.api.trace.Span.Kind.CONSUMER
import static io.opentelemetry.api.trace.Span.Kind.PRODUCER
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
@Unroll
abstract class AbstractRocketMqClientTest extends InstrumentationSpecification {

  @Shared
  DefaultMQProducer producer;

  @Shared
  DefaultMQPushConsumer consumer;

  @Shared
  private String sharedTopic = "opentelemetry-test-topic";

  @Shared
  private String groupName = "opentelemetry_group_name";

  @Shared
  private String brokerAddr = "127.0.0.1:10911"

  @Shared
  private String nameServerAddr = "127.0.0.1:9876"

  @Shared
  private SendResult sendResult;

  def setup() {
  }

  def "test rocketmq produce and concurrently consume"() {
    setup:
    producer = new DefaultMQProducer(groupName);
    producer.setNamesrvAddr(nameServerAddr);
    producer.start();
    consumer = new DefaultMQPushConsumer(groupName);
    consumer.setNamesrvAddr(nameServerAddr);
    consumer.subscribe(sharedTopic, "*");
    consumer.registerMessageListener(new MessageListenerConcurrently() {

      @Override
      ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                                                      ConsumeConcurrentlyContext context) {
        System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msgs);
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
      }
    });

    consumer.start();
    when:
    Message msg = new Message(sharedTopic, "TagA", ("Hello RocketMQ ").getBytes(RemotingHelper.DEFAULT_CHARSET)
    );
    sendResult = runUnderTrace("parent") {
      producer.send(msg);
    }

    then:
    sendResult.getSendStatus() == SendStatus.SEND_OK;
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name sharedTopic + " send"
          kind PRODUCER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
          }
        }
        span(2) {
          name sharedTopic + " process"
          kind CONSUMER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.consume_concurrently_status" "CONSUME_SUCCESS"
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long

          }
        }
      }
      cleanup:
      producer.shutdown();
      consumer.shutdown();
    }
  }

  def "test rocketmq produce and orderly consume"() {
    setup:
    producer = new DefaultMQProducer(groupName);
    producer.setNamesrvAddr(nameServerAddr);
    producer.start();
    consumer = new DefaultMQPushConsumer(groupName);
    consumer.setNamesrvAddr(nameServerAddr);
    consumer.subscribe(sharedTopic, "*");
    consumer.registerMessageListener(new MessageListenerOrderly() {
      @Override
       ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext consumeOrderlyContext) {
        System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msgs);
        return ConsumeOrderlyStatus.SUCCESS;
      }
    });

    consumer.start();
    when:
    Message msg = new Message(sharedTopic, "TagA", ("Hello RocketMQ").getBytes(RemotingHelper.DEFAULT_CHARSET)
    );
    sendResult = runUnderTrace("parent") {
      producer.send(msg);
    }

    then:
    sendResult.getSendStatus() == SendStatus.SEND_OK;
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name sharedTopic + " send"
          kind PRODUCER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
          }
        }
        span(2) {
          name sharedTopic + " process"
          kind CONSUMER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.consume_orderly_status" "SUCCESS"
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long

          }
        }
      }

      cleanup:
      producer.shutdown();
      consumer.shutdown();
    }
  }

  def "test rocketmq produce callback and orderly consume"() {
    setup:
    producer = new DefaultMQProducer(groupName);
    producer.setNamesrvAddr(nameServerAddr);
    producer.start();

    consumer = new DefaultMQPushConsumer(groupName);
    consumer.setNamesrvAddr(nameServerAddr);
    consumer.subscribe(sharedTopic, "*");
    consumer.registerMessageListener(new MessageListenerOrderly() {

      @Override
      public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext consumeOrderlyContext) {
        System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msgs);
        return ConsumeOrderlyStatus.SUCCESS;
      }
    });

    consumer.start();
    when:
    Message msg = new Message(sharedTopic, "TagA", ("Hello RocketMQ ").getBytes(RemotingHelper.DEFAULT_CHARSET)
    );
    runUnderTrace("parent") {
      producer.send(msg, new SendCallback() {
        @Override
        public void onSuccess(SendResult sendResult) {
          System.err.println("msgId: " + sendResult.getMsgId() + ", status: " + sendResult.getSendStatus());
        }
        @Override
        public void onException(Throwable e) {
          msg.getTopic();
          e.printStackTrace();
        }
      });
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name sharedTopic + " send"
          kind PRODUCER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.callback_result" "SEND_OK"
          }
        }
        span(2) {
          name sharedTopic + " process"
          kind CONSUMER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.consume_orderly_status" "SUCCESS"
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long

          }
        }
      }

      cleanup:
      producer.shutdown();
      consumer.shutdown();
    }
  }

}

