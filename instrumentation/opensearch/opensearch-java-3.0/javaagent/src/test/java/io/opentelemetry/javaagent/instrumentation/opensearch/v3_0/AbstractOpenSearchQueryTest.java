/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.URI;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractOpenSearchQueryTest {

  static final String INDEX_NAME = "test-search-index";
  OpenSearchClient openSearchClient;
  OpensearchContainer opensearch;
  URI httpHost;

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @BeforeAll
  void setUp() throws Exception {
    opensearch =
        new OpensearchContainer(DockerImageName.parse("opensearchproject/opensearch:1.3.6"))
            .withSecurityEnabled();
    opensearch.withEnv(
        "OPENSEARCH_JAVA_OPTS",
        "-Xmx256m -Xms256m -Dlog4j2.disableJmx=true -Dlog4j2.disable.jmx=true -XX:-UseContainerSupport");
    opensearch.start();
    httpHost = URI.create(opensearch.getHttpHostAddress());
    openSearchClient = buildOpenSearchClient();

    String documentId = "test-doc-1";

    CreateIndexRequest createIndexRequest =
        CreateIndexRequest.of(
            c ->
                c.index(INDEX_NAME)
                    .mappings(
                        TypeMapping.of(
                            t ->
                                t.properties(
                                    "message",
                                    p ->
                                        p.text(txt -> txt.fielddata(true).analyzer("standard"))))));

    openSearchClient.indices().create(createIndexRequest);

    TestDocument testDocument = TestDocument.create(documentId, "test message for search");
    IndexRequest<TestDocument> indexRequest =
        new IndexRequest.Builder<TestDocument>().index(INDEX_NAME).document(testDocument).build();

    openSearchClient.index(indexRequest);
    openSearchClient.indices().refresh(r -> r.index(INDEX_NAME));
  }

  private OpenSearchClient buildOpenSearchClient() throws Exception {
    HttpHost host = new HttpHost("https", httpHost.getHost(), httpHost.getPort());

    TrustStrategy acceptingTrustStrategy = (certificate, authType) -> true;
    SSLContext sslContext =
        SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
    TlsStrategy tlsStrategy =
        ClientTlsStrategyBuilder.create()
            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .setSslContext(sslContext)
            .build();
    PoolingAsyncClientConnectionManager connectionManager =
        PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(null, -1),
        new UsernamePasswordCredentials(
            opensearch.getUsername(), opensearch.getPassword().toCharArray()));

    return new OpenSearchClient(
        buildOpenSearchTransport(host, connectionManager, credentialsProvider));
  }

  protected OpenSearchTransport buildOpenSearchTransport(
      HttpHost host,
      PoolingAsyncClientConnectionManager connectionManager,
      BasicCredentialsProvider credentialsProvider) {
    return ApacheHttpClient5TransportBuilder.builder(host)
        .setHttpClientConfigCallback(
            httpClientBuilder ->
                httpClientBuilder
                    .setDefaultCredentialsProvider(credentialsProvider)
                    .setConnectionManager(connectionManager)
                    .setDefaultCredentialsProvider(credentialsProvider))
        .build();
  }

  @AfterAll
  void tearDown() {
    opensearch.stop();
  }
}
