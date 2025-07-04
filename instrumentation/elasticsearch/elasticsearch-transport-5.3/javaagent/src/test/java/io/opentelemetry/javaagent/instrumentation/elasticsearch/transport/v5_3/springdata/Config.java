/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata;

import java.io.File;
import java.util.Collections;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.transport.Netty3Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(
    basePackages =
        "io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata")
@ComponentScan(
    basePackages =
        "io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata")
class Config {
  private static final Logger logger = LoggerFactory.getLogger(Config.class);

  static File esWorkingDir;

  @Bean
  Node elasticSearchNode() throws Exception {
    if (esWorkingDir == null) {
      throw new IllegalStateException("elasticsearch working directory not set");
    }
    if (!esWorkingDir.exists()) {
      throw new IllegalStateException("elasticsearch working directory does not exist");
    }

    Settings settings =
        Settings.builder()
            .put("http.enabled", "false")
            .put("path.data", esWorkingDir.toString())
            .put("path.home", esWorkingDir.toString())
            .put("thread_pool.listener.size", 1)
            .put("transport.type", "netty3")
            .put("http.type", "netty3")
            .put("discovery.type", "single-node")
            .build();

    logger.info("ES work dir: {}", esWorkingDir);

    Node testNode =
        new Node(
            new Environment(InternalSettingsPreparer.prepareSettings(settings)),
            Collections.singletonList(Netty3Plugin.class)) {};
    testNode.start();
    // disable periodic refresh in InternalClusterInfoService as it creates spans that tests don't
    // expect
    testNode
        .client()
        .admin()
        .cluster()
        .updateSettings(
            new ClusterUpdateSettingsRequest()
                .transientSettings(
                    Collections.singletonMap(
                        "cluster.routing.allocation.disk.threshold_enabled", false)));

    return testNode;
  }

  @Bean
  ElasticsearchOperations elasticsearchTemplate(Node node) {
    return new ElasticsearchTemplate(node.client());
  }
}
