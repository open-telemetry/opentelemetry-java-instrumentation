/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfig;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.cassandra.v4_4.internal.CassandraTelemetryUtil;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
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
  void standardWrapHasNoStableTarget() {
    CassandraRequest request = execute(telemetry.wrap(session));

    assertThat(request.getServerTarget()).isNull();
    verify(session, never()).getContext();
  }

  @Test
  void explicitContactPointsProvideTheCompleteTarget() {
    CassandraRequest request =
        execute(
            telemetry.wrap(
                session,
                asList(
                    InetSocketAddress.createUnresolved("configured.example.com", 9042),
                    InetSocketAddress.createUnresolved("programmatic.example.com", 9142))));

    assertTarget(request, "configured.example.com:9042,programmatic.example.com:9142", null);
    verify(session, never()).getContext();
  }

  @Test
  void explicitContactPointsAreCapturedWhenWrapped() {
    List<InetSocketAddress> contactPoints =
        new ArrayList<>(
            singletonList(InetSocketAddress.createUnresolved("configured.example.com", 9142)));
    CqlSession wrappedSession = telemetry.wrap(session, contactPoints);

    contactPoints.clear();

    assertTarget(execute(wrappedSession), "configured.example.com", 9142);
  }

  @Test
  void capturedContactPointsIncludeDriverConfiguration() {
    if (emitStableDatabaseSemconv()) {
      configureContactPoints("configured.example.com:9042");
    }

    CassandraRequest request = execute(CassandraTelemetryUtil.wrap(telemetry, session, emptySet()));

    assertTarget(request, "configured.example.com", null);
  }

  @Test
  void capturedContactPointsCombineBuilderAndDriverConfiguration() {
    if (emitStableDatabaseSemconv()) {
      configureContactPoints("configured.example.com:9042");
    }

    CassandraRequest request =
        execute(
            CassandraTelemetryUtil.wrap(
                telemetry,
                session,
                singleton(
                    new DefaultEndPoint(
                        InetSocketAddress.createUnresolved("programmatic.example.com", 9142)))));

    assertTarget(request, "configured.example.com:9042,programmatic.example.com:9142", null);
  }

  private void configureContactPoints(String contactPoint) {
    DriverContext context = mock(DriverContext.class);
    DriverConfig config = mock(DriverConfig.class);
    DriverExecutionProfile profile = mock(DriverExecutionProfile.class);
    when(session.getContext()).thenReturn(context);
    when(context.getConfig()).thenReturn(config);
    when(config.getDefaultProfile()).thenReturn(profile);
    when(profile.getStringList(DefaultDriverOption.CONTACT_POINTS, emptyList()))
        .thenReturn(singletonList(contactPoint));
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
