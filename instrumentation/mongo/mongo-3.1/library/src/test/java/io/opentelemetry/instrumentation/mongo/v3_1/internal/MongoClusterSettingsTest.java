/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static org.assertj.core.api.Assertions.assertThat;

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
}
