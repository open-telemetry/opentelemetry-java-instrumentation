/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.jms;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.ClientDecorator;
import io.opentelemetry.trace.Tracer;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;

public class JMSDecorator extends ClientDecorator {
  public static final JMSDecorator DECORATE = new JMSDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.jms-1.1");

  public String spanNameForReceive(final Message message) {
    return toSpanName(message, null);
  }

  public String spanNameForConsumer(final Message message) {
    return toSpanName(message, null);
  }

  public String spanNameForProducer(final Message message, final Destination destination) {
    return toSpanName(message, destination);
  }

  private static final String TIBCO_TMP_PREFIX = "$TMP$";

  public static String toSpanName(final Message message, final Destination destination) {
    Destination jmsDestination = null;
    try {
      jmsDestination = message.getJMSDestination();
    } catch (final Exception e) {
    }
    if (jmsDestination == null) {
      jmsDestination = destination;
    }
    return toSpanName(jmsDestination);
  }

  public static String toSpanName(Destination destination) {
    try {
      if (destination instanceof Queue) {
        String queueName = ((Queue) destination).getQueueName();
        if (destination instanceof TemporaryQueue || queueName.startsWith(TIBCO_TMP_PREFIX)) {
          return "queue/<temporary>";
        } else {
          return "queue/" + queueName;
        }
      }
      if (destination instanceof Topic) {
        String topicName = ((Topic) destination).getTopicName();
        if (destination instanceof TemporaryTopic || topicName.startsWith(TIBCO_TMP_PREFIX)) {
          return "topic/<temporary>";
        } else {
          return "topic/" + topicName;
        }
      }
    } catch (final Exception e) {
    }
    return "destination";
  }
}
