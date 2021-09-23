/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws

import io.opentelemetry.instrumentation.test.asserts.TraceAssert

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class AwsSpan {

  static s3(TraceAssert traceAssert, int index, spanName, bucketName, method = "GET", parentSpan = null) {
    return traceAssert.span(index) {
      name spanName
      kind CLIENT
      if (index == 0) {
        hasNoParent()
      } else {
        childOf parentSpan
      }
      attributes {
        "aws.agent" "java-aws-sdk"
        "aws.endpoint" String
        "aws.operation" spanName.substring(3)
        "aws.service" "Amazon S3"
        "aws.bucket.name" bucketName
        "http.flavor" "1.1"
        "http.method" method
        "http.status_code" 200
        "http.url" String
        "net.peer.name" String
        "net.transport" IP_TCP
        "net.peer.port" { it == null || Number }
      }
    }
  }

  static sqs(TraceAssert traceAssert, int index, spanName, queueUrl = null, queueName = null, spanKind = CLIENT, parentSpan = null) {
    return traceAssert.span(index) {
      name spanName
      kind spanKind
      if (index == 0) {
        hasNoParent()
      } else {
        childOf parentSpan
      }
      attributes {
        "aws.agent" "java-aws-sdk"
        "aws.endpoint" String
        "aws.operation" spanName.substring(4)
        "aws.service" "AmazonSQS"
        "aws.queue.name" { it == null || it == queueName }
        "aws.queue.url" { it == null || it == queueUrl }
        "http.flavor" "1.1"
        "http.method" "POST"
        "http.status_code" 200
        "http.url" String
        "http.user_agent" { it == null || String }
        "net.peer.name" String
        "net.peer.port" { it == null || Number }
        "net.transport" IP_TCP
      }
    }
  }

  static sns(TraceAssert traceAssert, int index, spanName, parentSpan = null) {
    return traceAssert.span(index) {
      name spanName
      kind CLIENT
      if (index == 0) {
        hasNoParent()
      } else {
        childOf parentSpan
      }
      attributes {
        "aws.agent" "java-aws-sdk"
        "aws.endpoint" String
        "aws.operation" spanName.substring(4)
        "aws.service" "AmazonSNS"
        "http.flavor" "1.1"
        "http.method" "POST"
        "http.status_code" 200
        "http.url" String
        "net.peer.name" String
        "net.peer.port" { it == null || Number }
        "net.transport" IP_TCP
      }
    }
  }

}
