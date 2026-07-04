/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.TracingExecutionInterceptor;
import io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.SpringAwsUtil.TracingContext;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.messaging.Message;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

class SpringAwsSqsBatchProcessAttributesGetter
    implements MessagingAttributesGetter<Collection<Message<?>>, Void> {

  private static final VirtualField<Message<?>, TracingContext> tracingContextField =
      VirtualField.find(Message.class, TracingContext.class);

  @Override
  public String getSystem(Collection<Message<?>> messages) {
    return "aws_sqs";
  }

  @Nullable
  @Override
  public String getDestination(Collection<Message<?>> messages) {
    if (!messages.isEmpty()) {
      Message<?> message = messages.iterator().next();
      TracingContext tracingContext = tracingContextField.get(message);
      if (tracingContext != null) {
        ExecutionAttributes request = tracingContext.getRequest();
        SdkRequest sdkRequest =
            request.getAttribute(TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE);
        if (sdkRequest instanceof ReceiveMessageRequest receiveMessageRequest) {
          String queueUrl = receiveMessageRequest.queueUrl();
          if (queueUrl != null) {
            int i = queueUrl.lastIndexOf('/');
            if (i > 0) {
              return queueUrl.substring(i + 1);
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getDestinationTemplate(Collection<Message<?>> messages) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(Collection<Message<?>> messages) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(Collection<Message<?>> messages) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(Collection<Message<?>> messages) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(Collection<Message<?>> messages) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(Collection<Message<?>> messages) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(Collection<Message<?>> messages, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(Collection<Message<?>> messages) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(Collection<Message<?>> messages, @Nullable Void unused) {
    return (long) messages.size();
  }

  @Override
  public List<String> getMessageHeader(Collection<Message<?>> messages, String name) {
    return emptyList();
  }
}
