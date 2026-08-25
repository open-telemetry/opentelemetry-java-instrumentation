/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Collections.singletonList;
import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.AbstractElasticsearchTransportClientTest;
import java.io.File;
import java.net.InetAddress;
import java.util.UUID;
import javax.annotation.Nullable;
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
  void configuredAddressListIsTheWholeTarget() {
    TransportClient addressListClient = newClient();
    testing.runWithSpan(
        "setup",
        () -> {
          addressListClient.addTransportAddress(tcpPublishAddress);
          // nothing listens on this address; the configured target names it all the same
          addressListClient.addTransportAddress(addressThatIsDown());
          // adding an address makes the client reach out to it, which reports telemetry of its own
          clusterHealth(addressListClient);
        });
    testing.waitForTraces(1);
    testing.clearData();

    clusterHealth(addressListClient);

    assertConfiguredTarget(
        getAddress()
            + ":"
            + getPort()
            + ","
            + addressThatIsDown().getAddress()
            + ":"
            + addressThatIsDown().getPort());
  }

  @Test
  void theTargetDoesNotFollowLaterAddressChanges() {
    TransportClient singleAddressClient = newClient();
    testing.runWithSpan(
        "setup",
        () -> {
          singleAddressClient.addTransportAddress(tcpPublishAddress);
          // the target is read here, while the client names a single address
          clusterHealth(singleAddressClient);
          // a client can be given more addresses at any time; the target it already reported must
          // not change underneath the telemetry that was emitted with it
          singleAddressClient.addTransportAddress(addressThatIsDown());
          clusterHealth(singleAddressClient);
        });
    testing.waitForTraces(1);
    testing.clearData();

    clusterHealth(singleAddressClient);

    assertConfiguredTarget(null);
  }

  private static void clusterHealth(TransportClient client) {
    client.admin().cluster().prepareHealth().execute().actionGet(TIMEOUT);
  }

  /** Asserts the server of the elasticsearch span of a single request. */
  private void assertConfiguredTarget(@Nullable String addressList) {
    boolean stableAddressList = emitStableDatabaseSemconv() && addressList != null;
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            equalTo(
                                SERVER_ADDRESS,
                                !emitStableDatabaseSemconv()
                                    ? null
                                    : (stableAddressList ? addressList : getAddress())),
                            equalTo(
                                SERVER_PORT,
                                !emitStableDatabaseSemconv() || stableAddressList
                                    ? null
                                    : Long.valueOf(getPort())))));
  }

  private static TransportAddress addressThatIsDown() {
    return new InetSocketTransportAddress(
        InetAddress.getLoopbackAddress(), tcpPublishAddress.getPort() + 1);
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
}
