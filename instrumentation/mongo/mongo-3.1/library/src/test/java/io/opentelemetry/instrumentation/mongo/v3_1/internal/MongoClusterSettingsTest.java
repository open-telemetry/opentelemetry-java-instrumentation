/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterSettings;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoClusterSettings.LegacySrvTargetScope;
import java.util.List;
import org.junit.jupiter.api.Test;

class MongoClusterSettingsTest {

  @Test
  void srvConnectionStringOmitsCredentialsPathQueryAndFragment() {
    MongoServerTarget target =
        MongoClusterSettings.srvConnectionString(
            "mongodb+srv://user:password@cluster0.example.com/database?tls=true#fragment");

    assertThat(target.getAddress()).isEqualTo("mongodb+srv://cluster0.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void srvConnectionStringSchemeIsCaseInsensitive() {
    MongoServerTarget target =
        MongoClusterSettings.srvConnectionString("MoNgOdB+SrV://cluster0.example.com");

    assertThat(target.getAddress()).isEqualTo("mongodb+srv://cluster0.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void unsafeOrNonSrvConnectionStringsAreNotReported() {
    assertThat(
            MongoClusterSettings.srvConnectionString(
                "mongodb+srv://user%3Apassword%40cluster0.example.com"))
        .isNull();
    assertThat(MongoClusterSettings.srvConnectionString("mongodb://cluster0.example.com")).isNull();
    assertThat(MongoClusterSettings.srvConnectionString(null)).isNull();
  }

  @Test
  void nestedLegacySrvTargetDoesNotRestoreConsumedOuterTarget() {
    LegacySrvTargetScope outerScope =
        requireNonNull(
            MongoClusterSettings.openLegacySrvTargetScope(
                "mongodb+srv://outer.example.com/database"));
    try {
      assertThat(configuredTarget(directBuilder()).getAddress())
          .isEqualTo("mongodb+srv://outer.example.com");

      LegacySrvTargetScope innerScope =
          requireNonNull(
              MongoClusterSettings.openLegacySrvTargetScope(
                  "mongodb+srv://inner.example.com/database"));
      try {
        assertThat(configuredTarget(directBuilder()).getAddress())
            .isEqualTo("mongodb+srv://inner.example.com");
      } finally {
        innerScope.close();
      }

      MongoServerTarget target = configuredTarget(directBuilder());
      assertThat(target.getAddress()).isEqualTo("direct.example");
      assertThat(target.getPort()).isEqualTo(27018);
    } finally {
      outerScope.close();
    }
  }

  @Test
  void legacySrvTargetIsConsumedBeforeNonSrvReentry() {
    LegacySrvTargetScope outerScope =
        requireNonNull(
            MongoClusterSettings.openLegacySrvTargetScope(
                "mongodb+srv://outer.example.com/database"));
    try {
      assertThat(configuredTarget(directBuilder()).getAddress())
          .isEqualTo("mongodb+srv://outer.example.com");

      assertThat(
              MongoClusterSettings.openLegacySrvTargetScope(
                  "mongodb://nested.example.com/database"))
          .isNull();
      MongoServerTarget target = configuredTarget(directBuilder());
      assertThat(target.getAddress()).isEqualTo("direct.example");
      assertThat(target.getPort()).isEqualTo(27018);
    } finally {
      outerScope.close();
    }
  }

  private static ClusterSettings.Builder directBuilder() {
    ClusterSettings.Builder builder = ClusterSettings.builder();
    List<ServerAddress> hosts = singletonList(new ServerAddress("direct.example", 27018));
    builder.hosts(hosts);
    MongoClusterSettings.hosts(builder, hosts);
    return builder;
  }

  private static MongoServerTarget configuredTarget(ClusterSettings.Builder builder) {
    ClusterSettings settings = builder.build();
    MongoClusterSettings.built(builder, settings);
    return requireNonNull(MongoClusterSettings.configuredTarget(settings));
  }
}
