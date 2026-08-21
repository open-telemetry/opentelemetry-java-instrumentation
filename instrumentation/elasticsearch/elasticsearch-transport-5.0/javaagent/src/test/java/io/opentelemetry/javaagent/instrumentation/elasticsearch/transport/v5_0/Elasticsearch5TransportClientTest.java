/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.AbstractElasticsearchTransportClientTest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportAttributesGetter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.internal.InternalSettingsPreparer;
import org.elasticsearch.transport.Netty3Plugin;
import org.elasticsearch.transport.NodeDisconnectedException;
import org.elasticsearch.transport.TransportException;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Elasticsearch5TransportClientTest extends AbstractElasticsearchTransportClientTest {
  private static final Logger logger =
      LoggerFactory.getLogger(Elasticsearch5TransportClientTest.class);

  private static final String clusterName = UUID.randomUUID().toString();
  private static Node testNode;
  private static TransportAddress tcpPublishAddress;
  private static TransportClient client;

  @BeforeAll
  static void setUp(@TempDir File esWorkingDir) {
    logger.info("ES work dir: {}", esWorkingDir);

    Settings settings =
        Settings.builder()
            .put("path.home", esWorkingDir.getPath())
            .put("transport.type", "netty3")
            .put("http.type", "netty3")
            .put(CLUSTER_NAME_SETTING.getKey(), clusterName)
            .build();
    testNode =
        new Node(
            new Environment(InternalSettingsPreparer.prepareSettings(settings)),
            singletonList(Netty3Plugin.class)) {};
    cleanup.deferAfterAll(testNode);
    startNode(testNode);

    tcpPublishAddress =
        testNode.injector().getInstance(TransportService.class).boundAddress().publishAddress();

    client =
        new PreBuiltTransportClient(
            Settings.builder()
                // Since we use listeners to close spans this should make our span closing
                // deterministic which is good for tests
                .put("thread_pool.listener.size", 1)
                .put(CLUSTER_NAME_SETTING.getKey(), clusterName)
                .build());
    cleanup.deferAfterAll(client);
    client.addTransportAddress(tcpPublishAddress);
    testing.runWithSpan(
        "setup",
        () ->
            // this may potentially create multiple requests and therefore multiple spans, so we
            // wrap this call
            // into a top level trace to get exactly one trace in the result.
            client
                .admin()
                .cluster()
                .prepareHealth()
                .setWaitForYellowStatus()
                .execute()
                .actionGet(TIMEOUT));
    testing.waitForTraces(1);
  }

  @Override
  protected TransportClient client() {
    return client;
  }

  @Override
  protected String getAddress() {
    return tcpPublishAddress.getAddress();
  }

  @Override
  protected int getPort() {
    return tcpPublishAddress.getPort();
  }

  @Override
  protected boolean hasWriteVersion() {
    return false;
  }

  @Test
  void nodeDisconnectUsesExceptionClassErrorType() {
    NodeDisconnectedException error =
        new NodeDisconnectedException(
            new DiscoveryNode("unreachable", tcpPublishAddress, Version.CURRENT), "GetAction");
    Attributes attributes = extractEndAttributes(error);

    assertThat(attributes.get(ERROR_TYPE))
        .isEqualTo(emitStableDatabaseSemconv() ? NodeDisconnectedException.class.getName() : null);
  }

  @Test
  void noNodeAvailableUsesDeclaredStatus() {
    Attributes attributes =
        extractEndAttributes(new NoNodeAvailableException("no nodes are available"));

    assertThat(attributes.get(ERROR_TYPE)).isEqualTo(emitStableDatabaseSemconv() ? "503" : null);
  }

  @Test
  void plainElasticsearchExceptionUsesExceptionClassErrorType() {
    Attributes attributes = extractEndAttributes(new ElasticsearchException("plain error"));

    assertThat(attributes.get(ERROR_TYPE))
        .isEqualTo(emitStableDatabaseSemconv() ? ElasticsearchException.class.getName() : null);
  }

  @Test
  void transportExceptionWithGenericCauseUsesExceptionClassErrorType() {
    Attributes attributes =
        extractEndAttributes(
            new TransportException("transport error", new IOException("connection refused")));

    assertThat(attributes.get(ERROR_TYPE))
        .isEqualTo(emitStableDatabaseSemconv() ? TransportException.class.getName() : null);
  }

  @SuppressWarnings("unchecked")
  private static Attributes extractEndAttributes(Throwable error) {
    ElasticsearchTransportAttributesGetter delegate = new ElasticsearchTransportAttributesGetter();
    DbClientAttributesGetter<ElasticTransportRequest, ActionResponse> getter =
        (DbClientAttributesGetter<ElasticTransportRequest, ActionResponse>)
            Proxy.newProxyInstance(
                DbClientAttributesGetter.class.getClassLoader(),
                new Class<?>[] {DbClientAttributesGetter.class},
                (proxy, method, args) -> {
                  switch (method.getName()) {
                    case "getDbSystemName":
                      return delegate.getDbSystemName((ElasticTransportRequest) args[0]);
                    case "getDbNamespace":
                      return delegate.getDbNamespace((ElasticTransportRequest) args[0]);
                    case "getDbQueryText":
                      return delegate.getDbQueryText((ElasticTransportRequest) args[0]);
                    case "getDbOperationName":
                      return delegate.getDbOperationName((ElasticTransportRequest) args[0]);
                    case "getErrorType":
                      return delegate.getErrorType(
                          (ElasticTransportRequest) args[0],
                          (ActionResponse) args[1],
                          (Throwable) args[2]);
                    default:
                      return null;
                  }
                });
    AttributesExtractor<ElasticTransportRequest, ActionResponse> extractor =
        DbClientAttributesExtractor.create(getter);
    AttributesBuilder attributes = Attributes.builder();
    extractor.onEnd(
        attributes,
        Context.root(),
        ElasticTransportRequest.create(new Object(), new Object()),
        null,
        error);
    return attributes.build();
  }
}
