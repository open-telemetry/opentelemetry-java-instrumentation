/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

public interface DestinationAdapter {

  boolean isQueue();

  boolean isTopic();

  String getQueueName() throws Exception;

  String getTopicName() throws Exception;

  boolean isTemporaryQueue();

  boolean isTemporaryTopic();
}
