/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.couchbase.springdata;

import static java.util.Objects.requireNonNull;

import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;

@Configuration
@EnableCouchbaseRepositories(basePackages = "io.opentelemetry.instrumentation.couchbase.springdata")
@ComponentScan(basePackages = "io.opentelemetry.instrumentation.couchbase.springdata")
class CouchbaseConfig extends AbstractCouchbaseConfiguration {

  // These need to be set before this class can be used by Spring
  static CouchbaseEnvironment environment;
  static BucketSettings bucketSettings;

  @Override
  protected CouchbaseEnvironment getEnvironment() {
    return requireNonNull(environment);
  }

  @Override
  protected List<String> getBootstrapHosts() {
    return Collections.singletonList("127.0.0.1");
  }

  @Override
  protected String getBucketName() {
    return bucketSettings.name();
  }

  @Override
  protected String getBucketPassword() {
    return bucketSettings.password();
  }
}
