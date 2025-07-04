/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.AbstractElasticsearchTransportClientTest;
import java.io.File;
import java.util.Collections;
import java.util.UUID;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.node.Node;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractElasticsearch6TransportClientTest
    extends AbstractElasticsearchTransportClientTest {
  private static final Logger logger =
      LoggerFactory.getLogger(AbstractElasticsearch6TransportClientTest.class);

  private static final String clusterName = UUID.randomUUID().toString();
  private Node testNode;
  private TransportAddress tcpPublishAddress;
  private TransportClient client;

  @BeforeAll
  void setUp(@TempDir File esWorkingDir) {
    logger.info("ES work dir: {}", esWorkingDir);

    Settings settings =
        Settings.builder()
            .put("path.home", esWorkingDir.getPath())
            .put(CLUSTER_NAME_SETTING.getKey(), clusterName)
            .put("discovery.type", "single-node")
            .build();
    testNode = getNodeFactory().newNode(settings);
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
    client.addTransportAddress(tcpPublishAddress);
    testing.runWithSpan(
        "setup",
        () -> {
          // this may potentially create multiple requests and therefore multiple spans, so we wrap
          // this call
          // into a top level trace to get exactly one trace in the result.
          client
              .admin()
              .cluster()
              .prepareHealth()
              .setWaitForYellowStatus()
              .execute()
              .actionGet(TIMEOUT);
          // disable periodic refresh in InternalClusterInfoService as it creates spans that tests
          // don't expect
          client
              .admin()
              .cluster()
              .updateSettings(
                  new ClusterUpdateSettingsRequest()
                      .transientSettings(
                          Collections.singletonMap(
                              "cluster.routing.allocation.disk.threshold_enabled", false)));
        });
    testing.waitForTraces(1);
    testing.clearData();
  }

  @AfterAll
  void cleanUp() throws Exception {
    testNode.close();
  }

  protected abstract NodeFactory getNodeFactory();

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
  protected boolean hasNetworkType() {
    return true;
  }
}
