/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.couchbase;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;

import com.couchbase.client.java.bucket.BucketType;
import com.couchbase.client.java.cluster.BucketSettings;
import com.couchbase.client.java.cluster.DefaultBucketSettings;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import com.couchbase.mock.Bucket;
import com.couchbase.mock.BucketConfiguration;
import com.couchbase.mock.CouchbaseMock;
import com.couchbase.mock.http.query.QueryServer;
import com.couchbase.mock.httpio.HttpServer;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.LongAssertConsumer;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.StringAssertConsumer;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractCouchbaseTest {
  private static final Logger logger = LoggerFactory.getLogger(AbstractCouchbaseTest.class);

  protected static final String USERNAME = "Administrator";
  protected static final String PASSWORD = "password";

  protected static final BucketSettings bucketCouchbase =
      DefaultBucketSettings.builder()
          .enableFlush(true)
          .name("$testBucketName-cb")
          .password("test-pass")
          .type(BucketType.COUCHBASE)
          .quota(100)
          .build();
  protected static final BucketSettings bucketMemcache =
      DefaultBucketSettings.builder()
          .enableFlush(true)
          .name("$testBucketName-mem")
          .password("test-pass")
          .type(BucketType.MEMCACHED)
          .quota(100)
          .build();
  private final int port = PortUtils.findOpenPort();
  private CouchbaseMock mock;

  @BeforeAll
  void setUp() throws Exception {
    mock = new CouchbaseMock("127.0.0.1", port, 1, 1);
    Field httpServerFiled = CouchbaseMock.class.getDeclaredField("httpServer");
    httpServerFiled.setAccessible(true);
    HttpServer httpServer = (HttpServer) httpServerFiled.get(mock);
    httpServer.register("/query", new QueryServer());
    mock.start();
    logger.info("CouchbaseMock listening on localhost:{}", port);

    mock.createBucket(convert(bucketCouchbase));
    mock.createBucket(convert(bucketMemcache));
  }

  private static BucketConfiguration convert(BucketSettings bucketSettings) {
    BucketConfiguration configuration = new BucketConfiguration();
    configuration.name = bucketSettings.name();
    configuration.password = bucketSettings.password();
    configuration.type = Bucket.BucketType.valueOf(bucketSettings.type().name());
    configuration.numNodes = 1;
    configuration.numReplicas = 0;
    return configuration;
  }

  @AfterAll
  void cleanUp() {
    mock.stop();
  }

  protected DefaultCouchbaseEnvironment.Builder envBuilder(
      EnvBuilder envBuilder, BucketSettings bucketSettings) {
    return envBuilder.apply(bucketSettings, mock.getCarrierPort(bucketSettings.name()), port);
  }

  protected abstract DefaultCouchbaseEnvironment.Builder envBuilder(
      BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort);

  protected DefaultCouchbaseEnvironment.Builder envBuilder(BucketSettings bucketSettings) {
    return envBuilder(this::envBuilder, bucketSettings);
  }

  @FunctionalInterface
  public interface EnvBuilder {
    DefaultCouchbaseEnvironment.Builder apply(
        BucketSettings bucketSettings, int carrierDirectPort, int httpDirectPort);
  }

  /** Override to return true in subclasses that include network attributes (e.g., 2.6+). */
  protected boolean includesNetworkAttributes() {
    return false;
  }

  /**
   * Override to return true in subclasses where experimental attributes are enabled (when
   * otel.instrumentation.couchbase.experimental-span-attributes=true).
   */
  protected boolean includesExperimentalAttributes() {
    return Boolean.getBoolean("otel.instrumentation.couchbase.experimental-span-attributes");
  }

  protected String networkType() {
    return includesNetworkAttributes() && emitOldDatabaseSemconv() ? "ipv4" : null;
  }

  protected String networkPeerAddress() {
    return includesNetworkAttributes() ? "127.0.0.1" : null;
  }

  protected LongAssertConsumer networkPeerPort() {
    return includesNetworkAttributes() ? val -> val.isNotNull() : val -> val.isNull();
  }

  protected StringAssertConsumer experimentalAttribute() {
    return includesExperimentalAttributes() ? val -> val.isNotNull() : val -> val.isNull();
  }
}
