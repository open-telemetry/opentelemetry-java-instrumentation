/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for the message header selector applied by {@link MessagingAttributesExtractor}. */
class MessagingHeadersTest {

  private static final Map<String, String> MESSAGE = new LinkedHashMap<>();

  static {
    MESSAGE.put("Test-Message-Header", "one");
    MESSAGE.put("Test-Message-Other", "two");
    MESSAGE.put("Authorization", "secret");
  }

  @Test
  void capturesExactIncludedNames() {
    assertThat(capture(selector(singletonList("Test-Message-Header"), emptyList())))
        .containsOnly(headerEntry("Test-Message-Header", "one"));
  }

  @Test
  void capturesWildcardIncludedNames() {
    assertThat(capture(selector(singletonList("Test-Message-*"), emptyList())))
        .containsOnly(
            headerEntry("Test-Message-Header", "one"), headerEntry("Test-Message-Other", "two"));
  }

  @Test
  void capturesSingleCharacterWildcardIncludedNames() {
    assertThat(capture(selector(singletonList("Test-Message-Othe?"), emptyList())))
        .containsOnly(headerEntry("Test-Message-Other", "two"));
  }

  @Test
  void exclusionTakesPrecedenceOverInclusion() {
    assertThat(capture(selector(singletonList("*"), singletonList("Authorization"))))
        .containsOnly(
            headerEntry("Test-Message-Header", "one"), headerEntry("Test-Message-Other", "two"));
    assertThat(
            capture(
                selector(
                    asList("Test-Message-Header", "Authorization"),
                    singletonList("Authorization"))))
        .containsOnly(headerEntry("Test-Message-Header", "one"));
  }

  @Test
  void excludeOnlySelectorCapturesEveryOtherHeader() {
    assertThat(capture(selector(emptyList(), singletonList("Authorization"))))
        .containsOnly(
            headerEntry("Test-Message-Header", "one"), headerEntry("Test-Message-Other", "two"));
  }

  @Test
  void emptySelectorCapturesNothing() {
    assertThat(capture(selector(emptyList(), emptyList()))).isEmpty();
  }

  @Test
  void absentSelectorCapturesNothing() {
    assertThat(capture(null)).isEmpty();
  }

  @Test
  void matchingHeaderNameIsCapturedOnlyOnce() {
    assertThat(capture(selector(asList("Test-Message-Header", "Test-Message-*"), emptyList())))
        .containsOnly(
            headerEntry("Test-Message-Header", "one"), headerEntry("Test-Message-Other", "two"));
  }

  @Test
  void exactNamesAreCapturedWithoutHeaderNameEnumeration() {
    // simulates a third party getter that implements only getMessageHeader
    MapGetter getter = new MapGetter(false);

    AttributesBuilder attributes = Attributes.builder();
    extractor(getter, selector(singletonList("Test-Message-Header"), emptyList()))
        .onEnd(attributes, Context.root(), MESSAGE, null, null);

    assertThat(attributes.build().asMap()).containsOnly(headerEntry("Test-Message-Header", "one"));
  }

  @Test
  void wildcardCapturesNothingWithoutHeaderNameEnumeration() {
    // a third party getter that implements only getMessageHeader cannot resolve wildcards
    MapGetter getter = new MapGetter(false);

    AttributesBuilder attributes = Attributes.builder();
    extractor(getter, selector(singletonList("Test-Message-*"), emptyList()))
        .onEnd(attributes, Context.root(), MESSAGE, null, null);

    assertThat(attributes.build().asMap()).isEmpty();
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersSetterSelectsExactNames() {
    AttributesBuilder attributes = Attributes.builder();
    builder()
        .setCapturedHeaders(singletonList("Test-Message-Header"))
        .build()
        .onEnd(attributes, Context.root(), MESSAGE, null, null);

    assertThat(attributes.build().asMap()).containsOnly(headerEntry("Test-Message-Header", "one"));
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersSetterWithEmptyCollectionCapturesNothing() {
    AttributesBuilder attributes = Attributes.builder();
    builder()
        .setCapturedHeaders(emptyList())
        .build()
        .onEnd(attributes, Context.root(), MESSAGE, null, null);

    assertThat(attributes.build().asMap()).isEmpty();
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersSetterIgnoresWildcards() {
    AttributesBuilder attributes = Attributes.builder();
    builder()
        .setCapturedHeaders(asList("Test-Message-Header", "Test-Message-*", "Authorizatio?"))
        .build()
        .onEnd(attributes, Context.root(), MESSAGE, null, null);

    assertThat(attributes.build().asMap()).containsOnly(headerEntry("Test-Message-Header", "one"));
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersSetterWithOnlyWildcardsCapturesNothing() {
    AttributesBuilder attributes = Attributes.builder();
    builder()
        .setCapturedHeaders(singletonList("*"))
        .build()
        .onEnd(attributes, Context.root(), MESSAGE, null, null);

    assertThat(attributes.build().asMap()).isEmpty();
  }

  private static Map<AttributeKey<?>, Object> capture(IncludeExclude headers) {
    AttributesBuilder attributes = Attributes.builder();
    extractor(new MapGetter(true), headers).onEnd(attributes, Context.root(), MESSAGE, null, null);
    return attributes.build().asMap();
  }

  private static AttributesExtractor<Map<String, String>, Void> extractor(
      MapGetter getter, IncludeExclude headers) {
    MessagingAttributesExtractorBuilder<Map<String, String>, Void> builder =
        MessagingAttributesExtractor.builder(getter, MessagingOperationType.PROCESS, "process");
    if (headers != null) {
      builder.setHeaders(headers);
    }
    return builder.build();
  }

  private static MessagingAttributesExtractorBuilder<Map<String, String>, Void> builder() {
    return MessagingAttributesExtractor.builder(
        new MapGetter(true), MessagingOperationType.PROCESS, "process");
  }

  private static IncludeExclude selector(List<String> included, List<String> excluded) {
    return IncludeExclude.builder().setIncluded(included).setExcluded(excluded).build();
  }

  private static Map.Entry<AttributeKey<?>, Object> headerEntry(String name, String value) {
    String attributeName = v3Preview() ? name : name.replace('-', '_');
    return new SimpleEntry<>(
        stringArrayKey("messaging.header." + attributeName), singletonList(value));
  }

  private static final class MapGetter
      implements MessagingAttributesGetter<Map<String, String>, Void> {

    private final boolean enumerateNames;

    MapGetter(boolean enumerateNames) {
      this.enumerateNames = enumerateNames;
    }

    @Override
    public String getSystem(Map<String, String> request) {
      return null;
    }

    @Override
    public String getDestination(Map<String, String> request) {
      return null;
    }

    @Override
    public String getDestinationTemplate(Map<String, String> request) {
      return null;
    }

    @Override
    public boolean isTemporaryDestination(Map<String, String> request) {
      return false;
    }

    @Override
    public boolean isAnonymousDestination(Map<String, String> request) {
      return false;
    }

    @Override
    public String getConversationId(Map<String, String> request) {
      return null;
    }

    @Override
    public Long getMessageBodySize(Map<String, String> request) {
      return null;
    }

    @Override
    public Long getMessageEnvelopeSize(Map<String, String> request) {
      return null;
    }

    @Override
    public String getMessageId(Map<String, String> request, Void response) {
      return null;
    }

    @Override
    public String getClientId(Map<String, String> request) {
      return null;
    }

    @Override
    public Long getBatchMessageCount(Map<String, String> request, Void response) {
      return null;
    }

    @Override
    public List<String> getMessageHeader(Map<String, String> request, String name) {
      String value = request.get(name);
      return value == null ? emptyList() : singletonList(value);
    }

    @Override
    public Collection<String> getMessageHeaderNames(Map<String, String> request) {
      return enumerateNames ? new ArrayList<>(request.keySet()) : emptyList();
    }
  }
}
