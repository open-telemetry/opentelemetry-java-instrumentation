/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportServerTarget.Endpoint;
import org.elasticsearch.client.support.AbstractClient;
import org.junit.jupiter.api.Test;

class ElasticsearchTransportServerTargetsTest {

  @Test
  void updateReplacesTarget() {
    AbstractClient client = initializedClient();

    ElasticsearchTransportServerTargets.update(
        client, singletonList(new Endpoint("10.0.0.1", 9300)));
    ElasticsearchTransportServerTargets.update(
        client, singletonList(new Endpoint("10.0.0.2", 9301)));

    DbServerTarget target = ElasticsearchTransportServerTargets.get(client);
    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.2");
    assertThat(target.getPort()).isEqualTo(9301);
  }

  @Test
  void updateStateIsIndependentPerClient() {
    AbstractClient firstClient = initializedClient();
    AbstractClient secondClient = initializedClient();

    ElasticsearchTransportServerTargets.UpdateToken firstToken =
        ElasticsearchTransportServerTargets.beginUpdate(firstClient);
    ElasticsearchTransportServerTargets.UpdateToken secondToken =
        ElasticsearchTransportServerTargets.beginUpdate(secondClient);

    ElasticsearchTransportServerTargets.update(
        firstClient, firstToken, singletonList(new Endpoint("10.0.0.1", 9300)));
    ElasticsearchTransportServerTargets.update(
        secondClient, secondToken, singletonList(new Endpoint("10.0.0.2", 9300)));

    DbServerTarget firstTarget = ElasticsearchTransportServerTargets.get(firstClient);
    DbServerTarget secondTarget = ElasticsearchTransportServerTargets.get(secondClient);
    assertThat(firstTarget).isNotNull();
    assertThat(secondTarget).isNotNull();
    assertThat(firstTarget.getAddress()).isEqualTo("10.0.0.1");
    assertThat(secondTarget.getAddress()).isEqualTo("10.0.0.2");
  }

  @Test
  void initializingUpdateStatePreservesExistingState() {
    AbstractClient client = initializedClient();
    ElasticsearchTransportServerTargets.UpdateToken token =
        ElasticsearchTransportServerTargets.beginUpdate(client);

    ElasticsearchTransportServerTargets.initializeUpdateState(client);
    ElasticsearchTransportServerTargets.update(
        client, token, singletonList(new Endpoint("10.0.0.1", 9300)));

    DbServerTarget target = ElasticsearchTransportServerTargets.get(client);
    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1");
  }

  @Test
  void updateClearsTarget() {
    AbstractClient client = initializedClient();

    ElasticsearchTransportServerTargets.update(
        client, singletonList(new Endpoint("10.0.0.1", 9300)));
    ElasticsearchTransportServerTargets.update(client, emptyList());

    assertThat(ElasticsearchTransportServerTargets.get(client)).isNull();
  }

  @Test
  void staleUpdateCannotOverwriteNewerUpdate() {
    AbstractClient client = initializedClient();
    ElasticsearchTransportServerTargets.UpdateToken firstToken =
        ElasticsearchTransportServerTargets.beginUpdate(client);
    ElasticsearchTransportServerTargets.UpdateToken secondToken =
        ElasticsearchTransportServerTargets.beginUpdate(client);

    ElasticsearchTransportServerTargets.update(
        client, secondToken, singletonList(new Endpoint("10.0.0.2", 9301)));
    ElasticsearchTransportServerTargets.update(
        client, firstToken, singletonList(new Endpoint("10.0.0.1", 9300)));

    DbServerTarget target = ElasticsearchTransportServerTargets.get(client);
    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.2");
    assertThat(target.getPort()).isEqualTo(9301);
  }

  @Test
  void staleUpdateCannotRestoreClearedTarget() {
    AbstractClient client = initializedClient();
    ElasticsearchTransportServerTargets.UpdateToken firstToken =
        ElasticsearchTransportServerTargets.beginUpdate(client);
    ElasticsearchTransportServerTargets.UpdateToken secondToken =
        ElasticsearchTransportServerTargets.beginUpdate(client);

    ElasticsearchTransportServerTargets.update(client, secondToken, emptyList());
    ElasticsearchTransportServerTargets.update(
        client, firstToken, singletonList(new Endpoint("10.0.0.1", 9300)));

    assertThat(ElasticsearchTransportServerTargets.get(client)).isNull();
  }

  @Test
  void linkedClientUsesDelegateTarget() {
    AbstractClient client = initializedClient();
    AbstractClient delegate = initializedClient();
    ElasticsearchTransportServerTargets.setDelegate(client, delegate);

    assertThat(ElasticsearchTransportServerTargets.get(client)).isNull();

    ElasticsearchTransportServerTargets.update(
        delegate, singletonList(new Endpoint("10.0.0.1", 9301)));

    DbServerTarget target = ElasticsearchTransportServerTargets.get(client);
    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1");
    assertThat(target.getPort()).isEqualTo(9301);
  }

  private static AbstractClient initializedClient() {
    AbstractClient client = mock(AbstractClient.class);
    ElasticsearchTransportServerTargets.initializeUpdateState(client);
    return client;
  }
}
