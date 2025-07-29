/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3;

import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.AbstractElasticsearchNodeClientTest;
import java.io.File;
import java.util.Collections;
import java.util.UUID;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.transport.Netty3Plugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Elasticsearch53NodeClientTest extends AbstractElasticsearchNodeClientTest {
  private static final Logger logger = LoggerFactory.getLogger(Elasticsearch53NodeClientTest.class);

  private static final String clusterName = UUID.randomUUID().toString();
  private static Node testNode;
  private static Client client;

  @BeforeAll
  static void setUp(@TempDir File esWorkingDir) {
    logger.info("ES work dir: {}", esWorkingDir);

    Settings settings =
        Settings.builder()
            .put("path.home", esWorkingDir.getPath())
            // Since we use listeners to close spans this should make our span closing deterministic
            // which is good for tests
            .put("thread_pool.listener.size", 1)
            .put("transport.type", "netty3")
            .put("http.type", "netty3")
            .put(CLUSTER_NAME_SETTING.getKey(), clusterName)
            .put("discovery.type", "single-node")
            .build();
    testNode =
        new Node(
            new Environment(InternalSettingsPreparer.prepareSettings(settings)),
            Collections.singletonList(Netty3Plugin.class)) {};
    startNode(testNode);

    client = testNode.client();
    testing.runWithSpan(
        "setup",
        () -> {
          // this may potentially create multiple requests and therefore multiple spans, so we wrap
          // this call into a top level trace to get exactly one trace in the result.
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
  static void cleanUp() throws Exception {
    testNode.close();
  }

  @Override
  public Client client() {
    return client;
  }
}
