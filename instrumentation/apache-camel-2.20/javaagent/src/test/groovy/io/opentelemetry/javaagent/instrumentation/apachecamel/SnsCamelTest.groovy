/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.CreateTopicResult
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Ignore
import spock.lang.Shared

@Ignore("Does not work with localstack - X-Ray features needed")
class SnsCamelTest extends AgentInstrumentationSpecification {

  @Shared
  ConfigurableApplicationContext server
  @Shared
  AmazonSQSClient sqsClient
  @Shared
  AmazonSNSClient snsClient
  @Shared
  CamelContext camelContext

  def setupSpec() {
    def app = new SpringApplication(SnsConfig)
    server = app.run()
    camelContext = server.getBean(CamelContext)
    sqsClient = server.getBean("sqsClient")
    snsClient = server.getBean("snsClient")
  }

  def getQueueArn(String queueUrl) {
    return sqsClient.getQueueAttributes(
      new GetQueueAttributesRequest(queueUrl)
        .withAttributeNames("QueueArn")).getAttributes()
      .get("QueueArn")
  }

  def setQueuePolicy(String queueUrl, String queueArn) {
    sqsClient.setQueueAttributes(queueUrl, Collections.singletonMap("Policy", String.format(SQS_POLICY, queueArn)))
  }

  private static final String SQS_POLICY = "{" +
    "  \"Statement\": [" +
    "    {" +
    "      \"Effect\": \"Allow\"," +
    "      \"Principal\": \"*\"," +
    "      \"Action\": \"sqs:SendMessage\"," +
    "      \"Resource\": \"%s\"" +
    "    }]" +
    "}"

  def createAndSubscribeTopic(String topicName, String queueArn) {
    CreateTopicResult ctr = snsClient.createTopic(topicName)
    snsClient.subscribe(ctr.getTopicArn(), "sqs", queueArn)
    return ctr.getTopicArn()
  }

  def "AWS SDK SNS producer - camel SQS consumer"() {
    setup:
    String topicName = "snsCamelTest"
    String queueName = "snsCamelTest"

    String queueUrl = sqsClient.createQueue(queueName).getQueueUrl()
    String queueArn = getQueueArn(queueUrl)
    setQueuePolicy(queueUrl, queueArn)
    String topicArn = createAndSubscribeTopic(topicName, queueArn)

    when:
    snsClient.publish(topicArn, "Hello there!")

    then:
    assertTraces(12) {
      trace(0, 1) {

        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "CreateQueue"
            "aws.queue.name" queueName
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(1, 1) {

        span(0) {
          name "SQS.GetQueueAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "GetQueueAttributes"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(2, 1) {

        span(0) {
          name "SQS.SetQueueAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "SetQueueAttributes"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(3, 1) {

        span(0) {
          name "SNS.CreateTopic"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "CreateTopic"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(4, 1) {

        span(0) {
          name "SNS.Subscribe"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "Subscribe"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(5, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(6, 3) {
        span(0) {
          name "SNS.Publish"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "Publish"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
        span(1) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "http.user_agent" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
        span(2) {
          name "snsCamelTest"
          kind INTERNAL
          childOf span(0)
          attributes {
            "apache-camel.uri" "aws-sqs://${queueName}?amazonSQSClient=%23sqsClient"
            "messaging.destination" queueName
            "messaging.message_id" String
          }
        }
      }
      trace(7, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(8, 1) {
        span(0) {
          name "SQS.DeleteMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "DeleteMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(9, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(10, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(11, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
    }
  }

  def "camel SNS producer - camel SQS consumer"() {
    setup:
    String topicName = "snsCamelTest"
    String queueName = "snsCamelTest"

    String queueUrl = sqsClient.createQueue(queueName).getQueueUrl()
    String queueArn = getQueueArn(queueUrl)
    setQueuePolicy(queueUrl, queueArn)
    createAndSubscribeTopic(topicName, queueArn)

    ProducerTemplate template = camelContext.createProducerTemplate()

    when:
    template.sendBody("direct:input", "{\"type\": \"hello\"}")

    then:
    assertTraces(12) {
      trace(0, 1) {

        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "CreateQueue"
            "aws.queue.name" queueName
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(1, 1) {

        span(0) {
          name "SQS.GetQueueAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "GetQueueAttributes"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(2, 1) {

        span(0) {
          name "SQS.SetQueueAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "SetQueueAttributes"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(3, 1) {

        span(0) {
          name "SNS.CreateTopic"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "CreateTopic"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(4, 1) {

        span(0) {
          name "SNS.Subscribe"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "Subscribe"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(5, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(6, 5) {

        span(0) {
          name "input"
          kind INTERNAL
          hasNoParent()
          attributes {
            "apache-camel.uri" "direct://input"
          }
        }
        span(1) {
          name topicName
          kind INTERNAL
          childOf span(0)
          attributes {
            "apache-camel.uri" "aws-sns://${topicName}?amazonSNSClient=%23snsClient"
            "messaging.destination" topicName
          }
        }
        span(2) {
          name "SNS.Publish"
          kind CLIENT
          childOf span(1)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "Publish"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
        span(3) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(2)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "http.user_agent" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
        span(4) {
          name "snsCamelTest"
          kind INTERNAL
          childOf span(2)
          attributes {
            "apache-camel.uri" "aws-sqs://${queueName}?amazonSQSClient=%23sqsClient"
            "messaging.destination" queueName
            "messaging.message_id" String
          }
        }
      }
      trace(7, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(8, 1) {
        span(0) {
          name "SQS.DeleteMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "DeleteMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(9, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(10, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(11, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" {it == null || Number}
            "net.transport" "IP.TCP"
          }
        }
      }
    }
  }

}

