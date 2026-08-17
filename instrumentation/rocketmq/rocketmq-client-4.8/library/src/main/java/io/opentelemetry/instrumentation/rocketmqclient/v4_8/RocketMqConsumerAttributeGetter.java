/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.consumer.listener.ConsumeReturnType;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerAttributeGetter
    implements MessagingAttributesGetter<RocketMqConsumerRequest, ConsumeMessageContext> {

  @Override
  public String getSystem(RocketMqConsumerRequest request) {
    return "rocketmq";
  }

  @Nullable
  @Override
  public String getDestination(RocketMqConsumerRequest request) {
    return request.getDestination();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(RocketMqConsumerRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(RocketMqConsumerRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(RocketMqConsumerRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(RocketMqConsumerRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(RocketMqConsumerRequest request) {
    if (request.isBatch()) {
      // per-message attributes that vary across a batch belong on the span links
      return null;
    }
    byte[] body = request.getMessage().getBody();
    return body == null ? null : (long) body.length;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(RocketMqConsumerRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(
      RocketMqConsumerRequest request, @Nullable ConsumeMessageContext unused) {
    return request.getMessageId();
  }

  @Nullable
  @Override
  public String getClientId(RocketMqConsumerRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(
      RocketMqConsumerRequest request, @Nullable ConsumeMessageContext unused) {
    return request.isBatch() ? (long) request.getBatchSize() : null;
  }

  @Nullable
  @Override
  public String getErrorType(
      RocketMqConsumerRequest request,
      @Nullable ConsumeMessageContext response,
      @Nullable Throwable error) {
    return getErrorType(response);
  }

  /** Returns the error type of the consume operation, or {@code null} if it did not fail. */
  @Nullable
  static String getErrorType(@Nullable ConsumeMessageContext response) {
    if (response == null) {
      return null;
    }
    // the consume return type is inspected before the consume status because it carries
    // distinctions that the status loses: rocketmq coerces a null consume status to
    // RECONSUME_LATER before invoking the hook, so the status alone cannot tell a listener that
    // threw or returned null apart from one that asked for redelivery, and it reports TIME_OUT for
    // a listener that exceeded the configured consume timeout even when that listener eventually
    // returned a success status
    Map<String, String> props = response.getProps();
    String consumeReturnType = props == null ? null : props.get(MixAll.CONSUME_CONTEXT_TYPE);
    if (consumeReturnType != null) {
      return ConsumeReturnType.SUCCESS.name().equals(consumeReturnType) ? null : consumeReturnType;
    }
    return response.isSuccess() ? null : response.getStatus();
  }

  @Override
  public List<String> getMessageHeader(RocketMqConsumerRequest request, String name) {
    if (request.isBatch()) {
      // per-message attributes that vary across a batch belong on the span links; merging the
      // headers of every message into one multi-valued attribute would lose which message each
      // value came from
      return emptyList();
    }
    List<String> values = new ArrayList<>();
    for (MessageExt message : request.getMessages()) {
      String value = message.getProperties().get(name);
      if (value != null) {
        values.add(value);
      }
    }
    return values;
  }

  @Override
  public Collection<String> getMessageHeaderNames(RocketMqConsumerRequest request) {
    if (request.isBatch()) {
      // batched messages do not report headers, see getMessageHeader above
      return emptyList();
    }
    Set<String> names = new LinkedHashSet<>();
    for (MessageExt message : request.getMessages()) {
      Map<String, String> properties = message.getProperties();
      if (properties != null) {
        names.addAll(properties.keySet());
      }
    }
    return names;
  }
}
