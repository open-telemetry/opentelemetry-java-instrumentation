/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import static java.util.Collections.emptyList;

import io.nats.client.impl.Headers;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

class NatsRequestMessagingAttributesGetter
    implements MessagingAttributesGetter<NatsRequest, Object> {

  private final List<Pattern> temporaryPatterns;

  public NatsRequestMessagingAttributesGetter(List<Pattern> temporaryPatterns) {
    this.temporaryPatterns = temporaryPatterns;
  }

  @Override
  public String getSystem(NatsRequest request) {
    return "nats";
  }

  @Nullable
  @Override
  public String getDestination(NatsRequest request) {
    return request.getSubject();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(NatsRequest request) {
    Pattern pattern = getTemporaryPattern(request);
    return pattern == null ? null : pattern.pattern();
  }

  @Override
  public boolean isTemporaryDestination(NatsRequest request) {
    return getTemporaryPattern(request) != null;
  }

  @Override
  public boolean isAnonymousDestination(NatsRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(NatsRequest request) {
    return null;
  }

  @Override
  public Long getMessageBodySize(NatsRequest request) {
    return request.getDataSize();
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(NatsRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(NatsRequest request, @Nullable Object unused) {
    return null;
  }

  @Override
  public String getClientId(NatsRequest request) {
    return String.valueOf(request.getClientId());
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(NatsRequest request, @Nullable Object unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(NatsRequest request, String name) {
    Headers headers = request.getHeaders();
    if (headers == null) {
      return emptyList();
    }
    List<String> result = headers.get(name);
    return result == null ? emptyList() : result;
  }

  /**
   * @return the temporary pattern used for this request, or null
   */
  private Pattern getTemporaryPattern(NatsRequest request) {
    if (request.getSubject().startsWith(request.getInboxPrefix())) {
      return NatsSubjectPattern.compile(request.getInboxPrefix() + "*");
    }

    for (Pattern pattern : temporaryPatterns) {
      if (pattern.matcher(request.getSubject()).matches()) {
        return pattern;
      }
    }

    return null;
  }
}
