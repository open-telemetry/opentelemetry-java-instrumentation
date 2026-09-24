/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.AbstractElasticsearchTransportClientTest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportServerTarget;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportServerTargets;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.FilterClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.internal.InternalSettingsPreparer;
import org.elasticsearch.transport.Netty3Plugin;
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
  private static final String DOWN_HOST = "es.example.com";
  private static Node testNode;
  private static TransportAddress tcpPublishAddress;
  private static TransportAddress addressThatIsDown;
  private static TransportClient client;

  @BeforeAll
  static void setUp(@TempDir File esWorkingDir) throws UnknownHostException {
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
    // a configured endpoint that names a host nothing listens on, reachable only through the
    // loopback bytes it carries
    addressThatIsDown =
        new InetSocketTransportAddress(
            InetAddress.getByAddress(DOWN_HOST, InetAddress.getLoopbackAddress().getAddress()),
            tcpPublishAddress.getPort() + 1);

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
  void configuredAddressChangesBeforeFirstRequestAreSnapshotted() {
    TransportClient addressListClient = newClient();
    testing.runWithSpan(
        "setup",
        () -> {
          addressListClient.addTransportAddress(tcpPublishAddress);
          // nothing listens on this address; the configured target names it all the same
          addressListClient.addTransportAddress(addressThatIsDown);
          // adding an address makes the client reach out to it, which reports telemetry of its own
          clusterHealth(addressListClient);
        });
    testing.waitForTraces(1);
    testing.clearData();

    clusterHealth(addressListClient);

    assertConfiguredTarget(configuredAddressListWithMixedPorts(), null);
  }

  @Test
  void sharedDefaultPortIsOmitted() {
    TransportClient addressListClient = connectedClient();
    ElasticsearchTransportServerTargets.update(
        addressListClient,
        asList(
            new ElasticsearchTransportServerTarget.Endpoint(getAddress(), 9300),
            new ElasticsearchTransportServerTarget.Endpoint(DOWN_HOST, 9300)));

    clusterHealth(addressListClient);

    assertConfiguredTarget(configuredAddressListWithSharedPort(), null);
  }

  @Test
  void sharedNonDefaultPortIsIncludedWithEachAddress() {
    TransportClient addressListClient = connectedClient();
    ElasticsearchTransportServerTargets.update(
        addressListClient,
        asList(
            new ElasticsearchTransportServerTarget.Endpoint(getAddress(), 9400),
            new ElasticsearchTransportServerTarget.Endpoint(DOWN_HOST, 9400)));

    clusterHealth(addressListClient);

    assertConfiguredTarget(configuredAddressListWithSharedNonDefaultPort(), null);
  }

  @Test
  void concurrentAddressChangesPublishFinalSnapshot() {
    TransportClient addressListClient = connectedClient();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    cleanup.deferCleanup(executor::shutdownNow);
    CompletableFuture<Void> start = new CompletableFuture<>();
    CompletableFuture<Void> add =
        start.thenRunAsync(
            () -> addressListClient.addTransportAddress(addressThatIsDown), executor);
    CompletableFuture<Void> remove =
        start.thenRunAsync(
            () -> addressListClient.removeTransportAddress(addressThatIsDown), executor);

    testing.runWithSpan(
        "setup",
        () -> {
          start.complete(null);
          assertThat(add).succeedsWithin(Duration.ofSeconds(10));
          assertThat(remove).succeedsWithin(Duration.ofSeconds(10));
        });
    testing.waitForTraces(1);
    testing.clearData();

    boolean hasDownAddress = addressListClient.transportAddresses().contains(addressThatIsDown);
    clusterHealth(addressListClient);
    assertConfiguredTarget(hasDownAddress ? configuredAddressListWithMixedPorts() : null, null);
  }

  @Test
  void threeArgumentFilterClientTracksExplicitAddressChanges() {
    TransportClient singleAddressClient = newClient();
    Client filteredClient = new TestFilterClient(singleAddressClient);
    testing.runWithSpan(
        "setup",
        () -> {
          singleAddressClient.addTransportAddress(tcpPublishAddress);
          clusterHealth(filteredClient);
        });
    testing.waitForTraces(1);
    testing.clearData();

    clusterHealth(filteredClient);
    assertConfiguredTarget(null, null);
    testing.clearData();

    testing.runWithSpan("setup", () -> singleAddressClient.addTransportAddress(addressThatIsDown));
    testing.waitForTraces(1);
    testing.clearData();

    clusterHealth(filteredClient);
    assertConfiguredTarget(configuredAddressListWithMixedPorts(), null);
    testing.clearData();

    testing.runWithSpan(
        "setup", () -> singleAddressClient.removeTransportAddress(addressThatIsDown));
    testing.waitForTraces(1);
    testing.clearData();

    clusterHealth(filteredClient);
    assertConfiguredTarget(null, null);
  }

  private static void clusterHealth(Client client) {
    client.admin().cluster().prepareHealth().execute().actionGet(TIMEOUT);
  }

  private void assertConfiguredTarget(String addressList, Long sharedPort) {
    String serverAddress = addressList == null ? getAddress() : addressList;
    Long serverPort =
        addressList == null ? (getPort() == 9300 ? null : Long.valueOf(getPort())) : sharedPort;
    String stableSpanName =
        "cluster:monitor/health " + serverAddress + (serverPort == null ? "" : ":" + serverPort);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv() ? stableSpanName : "ClusterHealthAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            clusterHealthAttributes(
                                emitStableDatabaseSemconv() ? serverAddress : null,
                                emitStableDatabaseSemconv() ? serverPort : null))));
  }

  private String configuredAddressListWithMixedPorts() {
    return getAddress() + ":" + getPort() + "," + DOWN_HOST + ":" + addressThatIsDown.getPort();
  }

  private String configuredAddressListWithSharedPort() {
    return getAddress() + "," + DOWN_HOST;
  }

  private String configuredAddressListWithSharedNonDefaultPort() {
    return getAddress() + ":9400," + DOWN_HOST + ":9400";
  }

  private static TransportClient connectedClient() {
    TransportClient connectedClient = newClient();
    testing.runWithSpan(
        "setup",
        () -> {
          connectedClient.addTransportAddress(tcpPublishAddress);
          clusterHealth(connectedClient);
        });
    testing.waitForTraces(1);
    testing.clearData();
    return connectedClient;
  }

  private static TransportClient newClient() {
    TransportClient newClient =
        new PreBuiltTransportClient(
            Settings.builder()
                .put("thread_pool.listener.size", 1)
                // keep the background node sampler from reporting telemetry of its own
                .put("client.transport.nodes_sampler_interval", "5m")
                .put(CLUSTER_NAME_SETTING.getKey(), clusterName)
                .build());
    cleanup.deferCleanup(newClient);
    return newClient;
  }

  private static class TestFilterClient extends FilterClient {

    TestFilterClient(TransportClient delegate) {
      super(Settings.EMPTY, delegate.threadPool(), delegate);
    }
  }
}
