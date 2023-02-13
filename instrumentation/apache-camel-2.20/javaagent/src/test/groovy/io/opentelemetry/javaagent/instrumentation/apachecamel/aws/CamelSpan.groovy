/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws

import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class CamelSpan {

  static direct(TraceAssert traceAssert, int index, spanName) {
    return traceAssert.span(index) {
      name spanName
      kind INTERNAL
      hasNoParent()
      attributes {
        "apache-camel.uri" "direct://${spanName}"
      }
    }
  }

  static sqsProduce(TraceAssert traceAssert, int index, queueName, parentSpan = null) {
    return traceAssert.span(index) {
      name queueName
      kind INTERNAL
      if (index == 0) {
        hasNoParent()
      } else {
        childOf parentSpan
      }
      attributes {
        "apache-camel.uri" "aws-sqs://${queueName}?amazonSQSClient=%23sqsClient&delay=1000"
        "$SemanticAttributes.MESSAGING_DESTINATION_NAME" queueName
      }
    }
  }

  static sqsConsume(TraceAssert traceAssert, int index, queueName, parentSpan = null) {
    return traceAssert.span(index) {
      name queueName
      kind INTERNAL
      if (index == 0) {
        hasNoParent()
      } else {
        childOf parentSpan
      }
      attributes {
        "apache-camel.uri" "aws-sqs://${queueName}?amazonSQSClient=%23sqsClient&delay=1000"
        "$SemanticAttributes.MESSAGING_DESTINATION_NAME" queueName
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
      }
    }
  }

  static snsPublish(TraceAssert traceAssert, int index, topicName, parentSpan = null) {
    return traceAssert.span(index) {
      name topicName
      kind INTERNAL
      childOf parentSpan
      attributes {
        "apache-camel.uri" "aws-sns://${topicName}?amazonSNSClient=%23snsClient"
        "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topicName
      }
    }
  }

  static s3(TraceAssert traceAssert, int index, parentSpan = null) {
    return traceAssert.span(index) {
      name "aws-s3"
      kind INTERNAL
      childOf parentSpan
      attributes {
        "apache-camel.uri" "aws-s3://${bucketName}?amazonS3Client=%23s3Client"
      }
    }
  }
}
