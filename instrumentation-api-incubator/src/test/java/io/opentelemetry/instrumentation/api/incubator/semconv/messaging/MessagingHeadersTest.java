/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class MessagingHeadersTest {

  private static final Map<String, List<String>> MESSAGE = new LinkedHashMap<>();

  static {
    MESSAGE.put("Test-Message-Header", asList("one", "two"));
    MESSAGE.put("Test-Message-Other", singletonList("other"));
    MESSAGE.put("Authorization", singletonList("secret"));
    MESSAGE.put("*", singletonList("literal-star"));
    MESSAGE.put("Test-?", singletonList("literal-question"));
  }

  @Test
  void defaultCapturesNothing() {
    assertThat(capture(builder().build(), MESSAGE)).isEmpty();
  }

  @Test
  void emptyListCapturesNothing() {
    assertThat(capture(emptyList())).isEmpty();
  }

  @Test
  void capturesOnlyExactNamesPreservingSpellingAndMultipleValues() {
    assertThat(capture(asList("Test-Message-Header", "missing", "Test-Message-Header")))
        .containsOnly(
            entry(stringArrayKey("messaging.header.Test-Message-Header"), asList("one", "two")));
  }

  @Test
  void wildcardCharactersAreLiteralAndDoNotExpandToSensitiveHeaders() {
    assertThat(capture(asList("*", "Test-?", "Test-Message-*", "Authorizatio?")))
        .containsOnly(
            entry(stringArrayKey("messaging.header.*"), singletonList("literal-star")),
            entry(stringArrayKey("messaging.header.Test-?"), singletonList("literal-question")));
  }

  @Test
  void respectsCaseSensitiveLibraryLookup() {
    assertThat(capture(singletonList("test-message-header"))).isEmpty();
  }

  @Test
  void respectsCaseInsensitiveLibraryLookupWithoutChangingConfiguredName() {
    Map<String, List<String>> message = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    message.putAll(MESSAGE);
    assertThat(
            capture(
                builder().setCapturedHeaders(singletonList("test-message-header")).build(),
                message))
        .containsOnly(
            entry(stringArrayKey("messaging.header.test-message-header"), asList("one", "two")));
  }

  @Test
  void snapshotsConfiguredHeaders() {
    Collection<String> headers = new ArrayList<>(singletonList("Test-Message-Header"));
    MessagingAttributesExtractorBuilder<Map<String, List<String>>, Void> builder =
        builder().setCapturedHeaders(headers);
    headers.add("Authorization");
    AttributesExtractor<Map<String, List<String>>, Void> extractor = builder.build();
    builder.setCapturedHeaders(singletonList("Authorization"));
    assertThat(capture(extractor, MESSAGE))
        .containsOnly(
            entry(stringArrayKey("messaging.header.Test-Message-Header"), asList("one", "two")));
  }

  private static Map<AttributeKey<?>, Object> capture(Collection<String> headers) {
    return capture(builder().setCapturedHeaders(headers).build(), MESSAGE);
  }

  private static Map<AttributeKey<?>, Object> capture(
      AttributesExtractor<Map<String, List<String>>, Void> extractor,
      Map<String, List<String>> message) {
    AttributesBuilder attributes = Attributes.builder();
    extractor.onEnd(attributes, Context.root(), message, null, null);
    return attributes.build().asMap();
  }

  private static MessagingAttributesExtractorBuilder<Map<String, List<String>>, Void> builder() {
    return MessagingAttributesExtractor.builder(
        new MapGetter(), MessagingOperationType.PROCESS, "process");
  }

  private static final class MapGetter
      implements MessagingAttributesGetter<Map<String, List<String>>, Void> {

    @Override
    public String getSystem(Map<String, List<String>> request) {
      return null;
    }

    @Override
    public String getDestination(Map<String, List<String>> request) {
      return null;
    }

    @Override
    public String getDestinationTemplate(Map<String, List<String>> request) {
      return null;
    }

    @Override
    public boolean isTemporaryDestination(Map<String, List<String>> request) {
      return false;
    }

    @Override
    public boolean isAnonymousDestination(Map<String, List<String>> request) {
      return false;
    }

    @Override
    public String getConversationId(Map<String, List<String>> request) {
      return null;
    }

    @Override
    public String getMessageId(Map<String, List<String>> request, Void response) {
      return null;
    }

    @Override
    public String getClientId(Map<String, List<String>> request) {
      return null;
    }

    @Override
    public Long getBatchMessageCount(Map<String, List<String>> request, Void response) {
      return null;
    }

    @Override
    public List<String> getMessageHeader(Map<String, List<String>> request, String name) {
      return request.getOrDefault(name, emptyList());
    }
  }
}
