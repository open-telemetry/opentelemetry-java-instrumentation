/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfig;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.DefaultNode;
import com.datastax.oss.driver.internal.core.metadata.MetadataManager;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CassandraTelemetryTest {

  @Mock private Instrumenter<CassandraRequest, ExecutionInfo> instrumenter;
  @Mock private CqlSession session;
  @Mock private ResultSet resultSet;
  @Mock private ExecutionInfo executionInfo;
  @Captor private ArgumentCaptor<CassandraRequest> requestCaptor;

  private CassandraTelemetry telemetry;

  @BeforeEach
  void setUp() {
    telemetry = new CassandraTelemetry(instrumenter);
    when(instrumenter.start(any(), any())).thenReturn(Context.root());
    when(session.execute("SELECT 1")).thenReturn(resultSet);
    when(resultSet.getExecutionInfo()).thenReturn(executionInfo);
  }

  @Test
  void standardWrapCapturesASingleConfiguredTarget() {
    if (emitStableDatabaseSemconv()) {
      InternalDriverContext context = configuredContext("configured.example.com:9042");
      MetadataManager metadataManager = context.getMetadataManager();
      DefaultNode node = metadataManager.getContactPoints().iterator().next();
      when(node.getEndPoint())
          .thenReturn(
              new DefaultEndPoint(
                  InetSocketAddress.createUnresolved("configured.example.com", 9042)));
    }

    CassandraRequest request = execute(telemetry.wrap(session));

    assertTarget(request, "configured.example.com", null);
  }

  @Test
  void explicitContactPointsCoverASessionWithoutConfigurationProvenance() {
    CassandraRequest request =
        execute(
            telemetry.wrap(
                session,
                singletonList(InetSocketAddress.createUnresolved("configured.example.com", 9142))));

    assertTarget(request, "configured.example.com", 9142);
  }

  @Test
  void customSessionWithoutConfigurationProvenanceHasNoStableTarget() {
    if (emitStableDatabaseSemconv()) {
      when(session.getContext()).thenReturn(mock(DriverContext.class));
    }

    CassandraRequest request = execute(telemetry.wrap(session));

    assertThat(request.getServerTarget()).isNull();
  }

  @Test
  void configurationAndMetadataMismatchHasNoStableTarget() {
    if (emitStableDatabaseSemconv()) {
      InternalDriverContext context = configuredContext("configured.example.com:9042");
      MetadataManager metadataManager = context.getMetadataManager();
      DefaultNode node = metadataManager.getContactPoints().iterator().next();
      when(node.getEndPoint())
          .thenReturn(
              new DefaultEndPoint(
                  InetSocketAddress.createUnresolved("different.example.com", 9042)));
    }

    CassandraRequest request = execute(telemetry.wrap(session));

    assertThat(request.getServerTarget()).isNull();
  }

  private InternalDriverContext configuredContext(String contactPoint) {
    InternalDriverContext context = mock(InternalDriverContext.class);
    DriverConfig config = mock(DriverConfig.class);
    DriverExecutionProfile profile = mock(DriverExecutionProfile.class);
    MetadataManager metadataManager = mock(MetadataManager.class);
    DefaultNode node = mock(DefaultNode.class);
    when(session.getContext()).thenReturn(context);
    when(context.getConfig()).thenReturn(config);
    when(config.getDefaultProfile()).thenReturn(profile);
    when(profile.getStringList(DefaultDriverOption.CONTACT_POINTS, emptyList()))
        .thenReturn(singletonList(contactPoint));
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(singleton(node));
    return context;
  }

  private CassandraRequest execute(CqlSession wrappedSession) {
    wrappedSession.execute("SELECT 1");
    verify(instrumenter).start(any(), requestCaptor.capture());
    return requestCaptor.getValue();
  }

  private static void assertTarget(CassandraRequest request, String address, Integer port) {
    DbServerTarget target = request.getServerTarget();
    if (emitStableDatabaseSemconv()) {
      assertThat(target).isNotNull();
      assertThat(target.getAddress()).isEqualTo(address);
      assertThat(target.getPort()).isEqualTo(port);
    } else {
      assertThat(target).isNull();
    }
  }
}
