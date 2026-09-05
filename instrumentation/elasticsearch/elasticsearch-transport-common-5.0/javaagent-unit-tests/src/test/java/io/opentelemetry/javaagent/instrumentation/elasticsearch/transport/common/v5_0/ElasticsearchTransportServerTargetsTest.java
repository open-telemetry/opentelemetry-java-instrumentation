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
    AbstractClient client = mock(AbstractClient.class);

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
  void updateLockIsStableAndPerClient() {
    AbstractClient firstClient = mock(AbstractClient.class);
    AbstractClient secondClient = mock(AbstractClient.class);

    ElasticsearchTransportServerTargets.initializeUpdateLock(firstClient);
    Object firstLock = ElasticsearchTransportServerTargets.getUpdateLock(firstClient);
    ElasticsearchTransportServerTargets.initializeUpdateLock(firstClient);
    ElasticsearchTransportServerTargets.initializeUpdateLock(secondClient);

    assertThat(firstLock).isNotNull();
    assertThat(ElasticsearchTransportServerTargets.getUpdateLock(firstClient)).isSameAs(firstLock);
    assertThat(ElasticsearchTransportServerTargets.getUpdateLock(secondClient))
        .isNotSameAs(firstLock);
  }

  @Test
  void updateClearsTarget() {
    AbstractClient client = mock(AbstractClient.class);

    ElasticsearchTransportServerTargets.update(
        client, singletonList(new Endpoint("10.0.0.1", 9300)));
    ElasticsearchTransportServerTargets.update(client, emptyList());

    assertThat(ElasticsearchTransportServerTargets.get(client)).isNull();
  }

  @Test
  void linkedClientUsesDelegateTarget() {
    AbstractClient client = mock(AbstractClient.class);
    AbstractClient delegate = mock(AbstractClient.class);
    ElasticsearchTransportServerTargets.setDelegate(client, delegate);

    assertThat(ElasticsearchTransportServerTargets.get(client)).isNull();

    ElasticsearchTransportServerTargets.update(
        delegate, singletonList(new Endpoint("10.0.0.1", 9301)));

    DbServerTarget target = ElasticsearchTransportServerTargets.get(client);
    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1");
    assertThat(target.getPort()).isEqualTo(9301);
  }
}
