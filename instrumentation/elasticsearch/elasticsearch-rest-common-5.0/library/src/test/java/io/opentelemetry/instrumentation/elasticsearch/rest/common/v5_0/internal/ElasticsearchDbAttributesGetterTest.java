/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.NetworkPeerCapture;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ElasticsearchDbAttributesGetterTest {

  private static final String SEARCH_BODY =
      "{\"query\":{\"match\":{\"title\":\"secret user data\"}}}";
  private static final String SANITIZED_BODY = "{\"query\":{\"match\":{\"title\":\"?\"}}}";
  private final ElasticsearchDbAttributesGetter getter =
      new ElasticsearchDbAttributesGetter(false, null);

  /** Records the bodies it is given and returns whatever it was configured to return. */
  private static class RecordingSanitizer implements UnaryOperator<String> {
    final List<String> sanitized = new ArrayList<>();
    final String result;

    RecordingSanitizer(String result) {
      this.result = result;
    }

    @Override
    public String apply(String body) {
      sanitized.add(body);
      return result;
    }
  }

  @Test
  void returnsTheSanitizedBody() {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isEqualTo(SANITIZED_BODY);
    assertThat(sanitizer.sanitized).containsExactly(SEARCH_BODY);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/_search",
        "_search",
        "/test-index/_search?preference=local",
        "test-index/_search?preference=local",
        "/_msearch",
        "/test-index/_msearch",
        "/_search/template",
        "_search/template",
        "/test-index/_search/template",
        "/_msearch/template",
        "/test-index/_msearch/template",
        "/_async_search",
        "/test-index/_async_search",
        "/test-index/_doc/_search",
        "test-index/_doc/_search",
        "/_render/template",
        "/_render/template/private-template",
        "/test-index/_terms_enum",
        "/test-index/_eql/search",
        "test-index/_eql/search"
      })
  void recognizesSearchPathWithoutEndpointDefinition(String endpoint) {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    ElasticsearchRestRequest request =
        ElasticsearchRestRequest.create(
            "POST", endpoint, null, new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON));

    assertThat(getter.getDbQueryText(request)).isEqualTo(SANITIZED_BODY);
    assertThat(sanitizer.sanitized).containsExactly(SEARCH_BODY);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/_search/scroll",
        "/test-index/_rollup_search",
        "/_application/search_application/private-app/_search"
      })
  void rejectsNonSearchPathWithoutEndpointDefinition(String endpoint) {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    ElasticsearchRestRequest request =
        ElasticsearchRestRequest.create(
            "POST", endpoint, null, new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON));

    assertThat(getter.getDbQueryText(request)).isNull();
    assertThat(sanitizer.sanitized).isEmpty();
  }

  @Test
  void dropsBodyWhenTheSanitizerRejectsIt() {
    // the sanitizer returns null when it cannot sanitize the body, which must never fall back to
    // capturing it raw
    RecordingSanitizer sanitizer = new RecordingSanitizer(null);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isNull();
  }

  @ParameterizedTest
  @MethodSource("multiSearchEndpoints")
  void joinsMultiSearchNdJsonLinesBeforeSanitizing(
      String endpointName, String requestPath, String endpointRoute) {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    String body =
        "{\"index\":\"private-index\"}\n"
            + "{\"query\":{\"match\":{\"title\":\"secret\"}}}\n"
            + "{}\n"
            + "{\"id\":\"private-template\"}\n";
    ElasticsearchRestRequest request =
        ElasticsearchRestRequest.create(
            "POST",
            requestPath,
            new ElasticsearchEndpointDefinition(endpointName, new String[] {endpointRoute}, true),
            new StringEntity(body, ContentType.APPLICATION_JSON));

    assertThat(getter.getDbQueryText(request)).isEqualTo(SANITIZED_BODY);
    // the line breaks are dropped while reading, so the sanitizer sees the values back to back
    assertThat(sanitizer.sanitized)
        .containsExactly(
            "{\"index\":\"private-index\"}"
                + "{\"query\":{\"match\":{\"title\":\"secret\"}}}"
                + "{}"
                + "{\"id\":\"private-template\"}");
  }

  @Test
  void capturesRawBodyWhenSanitizationDisabled() {
    // sanitization explicitly disabled: capture the body verbatim
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, null);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isEqualTo(SEARCH_BODY);
  }

  @Test
  void capturesNothingWhenCaptureDisabled() {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(false, sanitizer);

    assertThat(
            getter.getDbQueryText(
                searchRequest(new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON))))
        .isNull();
    assertThat(sanitizer.sanitized).isEmpty();
  }

  @Test
  void capturesNothingForNonSearchEndpoint() {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    ElasticsearchRestRequest request =
        ElasticsearchRestRequest.create(
            "PUT",
            "/test-index/_doc/1",
            new ElasticsearchEndpointDefinition("PUT", new String[] {"/{index}/_doc/{id}"}, false),
            new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON));

    assertThat(getter.getDbQueryText(request)).isNull();
    assertThat(sanitizer.sanitized).isEmpty();
  }

  @Test
  void doesNotReadNonRepeatableEntity() {
    // a non-repeatable entity must never be read, otherwise the request body would be consumed
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    HttpEntity entity =
        new InputStreamEntity(new ByteArrayInputStream(SEARCH_BODY.getBytes(UTF_8)));

    assertThat(getter.getDbQueryText(searchRequest(entity))).isNull();
    assertThat(sanitizer.sanitized).isEmpty();
  }

  @Test
  void dropsBodyWhenReadingFails() {
    RecordingSanitizer sanitizer = new RecordingSanitizer(SANITIZED_BODY);
    ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(true, sanitizer);
    HttpEntity entity =
        new StringEntity(SEARCH_BODY, ContentType.APPLICATION_JSON) {
          @Override
          public InputStream getContent() {
            return new InputStream() {
              private boolean firstRead = true;

              @Override
              public int read() throws IOException {
                if (firstRead) {
                  firstRead = false;
                  return '{';
                }
                throw new IOException("test");
              }

              @Override
              public int read(byte[] buffer, int offset, int length) throws IOException {
                int value = read();
                buffer[offset] = (byte) value;
                return 1;
              }
            };
          }
        };

    assertThat(getter.getDbQueryText(searchRequest(entity))).isNull();
    assertThat(sanitizer.sanitized).isEmpty();
  }

  @Test
  void capturesNetworkPeerFromRequest() {
    ElasticsearchRestRequest request = ElasticsearchRestRequest.create("GET", "/");
    Context context = request.getNetworkPeerCapture().storeInContext(Context.root());
    NetworkPeerCapture.capture(
        context, new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200));

    assertThat(getter.getNetworkPeerAddress(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "127.0.0.1" : null);
    assertThat(getter.getNetworkPeerPort(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? 9200 : null);
    assertThat(extractAttributes(request))
        .isEqualTo(
            emitStableDatabaseSemconv()
                ? Attributes.of(NETWORK_PEER_ADDRESS, "127.0.0.1", NETWORK_PEER_PORT, 9200L)
                : Attributes.empty());
  }

  @Test
  void doesNotResolveConfiguredHostname() {
    ElasticsearchRestRequest request = ElasticsearchRestRequest.create("GET", "/");
    Context context = request.getNetworkPeerCapture().storeInContext(Context.root());
    NetworkPeerCapture.capture(context, InetSocketAddress.createUnresolved("search.example", 9200));

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
    assertThat(extractAttributes(request)).isEqualTo(Attributes.empty());
  }

  @Test
  void handlesMissingPeer() {
    ElasticsearchRestRequest request = ElasticsearchRestRequest.create("GET", "/");

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
    assertThat(extractAttributes(request)).isEqualTo(Attributes.empty());
  }

  private static Stream<Arguments> multiSearchEndpoints() {
    return Stream.of(
        argumentSet("_msearch", "msearch", "/test-index/_msearch", "/{index}/_msearch"),
        argumentSet(
            "_msearch/template",
            "msearch_template",
            "/test-index/_msearch/template",
            "/{index}/_msearch/template"));
  }

  private static ElasticsearchRestRequest searchRequest(HttpEntity httpEntity) {
    return ElasticsearchRestRequest.create(
        "POST",
        "/test-index/_search",
        new ElasticsearchEndpointDefinition("SEARCH", new String[] {"/{index}/_search"}, true),
        httpEntity);
  }

  private Attributes extractAttributes(ElasticsearchRestRequest request) {
    AttributesBuilder attributes = Attributes.builder();
    DbClientAttributesExtractor.create(getter)
        .onEnd(attributes, Context.root(), request, null, null);
    return attributes.build();
  }
}
