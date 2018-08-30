package util

import com.anotherchrisberry.spock.extensions.retry.RetryOnFailure
import com.couchbase.client.core.env.AbstractServiceConfig
import com.couchbase.client.core.env.KeyValueServiceConfig
import com.couchbase.client.core.env.QueryServiceConfig
import com.couchbase.client.core.env.SearchServiceConfig
import com.couchbase.client.core.env.ViewServiceConfig
import com.couchbase.client.core.metrics.DefaultLatencyMetricsCollectorConfig
import com.couchbase.client.core.metrics.DefaultMetricsCollectorConfig
import com.couchbase.client.core.utils.Base64
import com.couchbase.client.java.Bucket
import com.couchbase.client.java.CouchbaseCluster
import com.couchbase.client.java.bucket.BucketType
import com.couchbase.client.java.cluster.AuthDomain
import com.couchbase.client.java.cluster.BucketSettings
import com.couchbase.client.java.cluster.ClusterManager
import com.couchbase.client.java.cluster.DefaultBucketSettings
import com.couchbase.client.java.cluster.UserRole
import com.couchbase.client.java.cluster.UserSettings
import com.couchbase.client.java.env.CouchbaseEnvironment
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment
import com.couchbase.client.java.query.Index
import com.couchbase.client.java.query.N1qlParams
import com.couchbase.client.java.query.N1qlQuery
import com.couchbase.client.java.query.consistency.ScanConsistency
import com.couchbase.client.java.view.DefaultView
import com.couchbase.client.java.view.DesignDocument
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.testcontainers.couchbase.CouchbaseContainer
import spock.lang.Requires
import spock.lang.Shared

import java.util.concurrent.TimeUnit

// Do not run tests locally on Java7 since testcontainers are not compatible with Java7
// It is fine to run on CI because CI provides couchbase externally, not through testcontainers
@Requires({ "true" == System.getenv("CI") || jvm.java8Compatible })

// Couchbase client sometimes throws com.couchbase.client.java.error.TemporaryFailureException.
// Lets automatically retry to avoid the test from failing completely.
@RetryOnFailure(times = 3, delaySeconds = 1)
abstract class AbstractCouchbaseTest extends AgentTestRunner {

  private static final USERNAME = "Administrator"
  private static final PASSWORD = "password"
  private static final OkHttpClient HTTP_CLIENT = OkHttpUtils.client()

  @Shared
  private String testBucketName = this.getClass().simpleName

  @Shared
  protected bucketCouchbase = DefaultBucketSettings.builder()
    .enableFlush(true)
    .name("$testBucketName-cb")
    .password("test-pass")
    .type(BucketType.COUCHBASE)
    .quota(100)
    .build()

  @Shared
  protected bucketMemcache = DefaultBucketSettings.builder()
    .enableFlush(true)
    .name("$testBucketName-mem")
    .password("test-pass")
    .type(BucketType.MEMCACHED)
    .quota(100)
    .build()

  @Shared
  protected bucketEphemeral = DefaultBucketSettings.builder()
    .enableFlush(true)
    .name("$testBucketName-emp")
    .password("test-pass")
    .type(BucketType.EPHEMERAL)
    .quota(100)
    .build()

  /*
    Note: type here has to stay undefined, otherwise tests will fail in CI in Java 7 because
    'testcontainers' are built for Java 8 and Java 7 cannot load this class.
   */
  @Shared
  def couchbaseContainer
  @Shared
  protected CouchbaseCluster cluster
  @Shared
  protected CouchbaseEnvironment environment
  @Shared
  protected ClusterManager manager

  def setupSpec() {

    /*
      CI will provide us with couchbase container running along side our build.
      When building locally, however, we need to take matters into our own hands
      and we use 'testcontainers' for this.
     */
    if (false && "true" != System.getenv("CI")) {
      couchbaseContainer = new CouchbaseContainer(
        clusterUsername: USERNAME,
        clusterPassword: PASSWORD)
      couchbaseContainer.start()
      environment = couchbaseContainer.getCouchbaseEnvironment()
      cluster = couchbaseContainer.getCouchbaseCluster()
      println "Couchbase container started"
    } else {
      initCluster()
      environment = envBuilder().build()
      cluster = CouchbaseCluster.create(environment)
      println "Using provided couchbase"
    }
    manager = cluster.clusterManager(USERNAME, PASSWORD)

    resetBucket(bucketCouchbase)
    resetBucket(bucketMemcache)
    resetBucket(bucketEphemeral)
  }

  def cleanupSpec() {
    // Close all buckets and disconnect
    cluster?.disconnect()

    couchbaseContainer?.stop()
  }

  protected DefaultCouchbaseEnvironment.Builder envBuilder() {
    def timeout = TimeUnit.MINUTES.toMillis(1)
    return DefaultCouchbaseEnvironment.builder()
    // settings to try to reduce variability in the tests:
      .configPollInterval(0)
      .configPollFloorInterval(0)
      .runtimeMetricsCollectorConfig(DefaultMetricsCollectorConfig.create(0, TimeUnit.DAYS))
      .networkLatencyMetricsCollectorConfig(DefaultLatencyMetricsCollectorConfig.create(0, TimeUnit.DAYS))
      .queryServiceConfig(QueryServiceConfig.create(10, 10, AbstractServiceConfig.NO_IDLE_TIME))
      .searchServiceConfig(SearchServiceConfig.create(10, 10, AbstractServiceConfig.NO_IDLE_TIME))
      .viewServiceConfig(ViewServiceConfig.create(10, 10, AbstractServiceConfig.NO_IDLE_TIME))
      .keyValueServiceConfig(KeyValueServiceConfig.create(10))
      .computationPoolSize(1)
      .analyticsTimeout(timeout)
      .connectTimeout(timeout)
      .kvTimeout(timeout)
      .managementTimeout(timeout)
      .queryTimeout(timeout)
      .searchTimeout(timeout)
      .viewTimeout(timeout)
  }


  protected void initCluster() {
    callCouchbaseRestAPI("/pools/default", new FormBody.Builder().add("memoryQuota", "1000").add("indexMemoryQuota", "300").build())
    callCouchbaseRestAPI("/node/controller/setupServices", new FormBody.Builder().add("services", "kv,index,n1ql,fts").build(), ["cannot change node services after cluster is provisioned"])
    callCouchbaseRestAPI("/settings/web", new FormBody.Builder().add("username", USERNAME).add("password", PASSWORD).add("port", "8091").build())
    callCouchbaseRestAPI("/settings/indexes", RequestBody.create(FormBody.CONTENT_TYPE, "indexerThreads=0&logLevel=info&maxRollbackPoints=5&storageMode=memory_optimized"))
  }

  /**
   * Adapted from CouchbaseContainer.callCouchbaseRestAPI()
   */
  protected void callCouchbaseRestAPI(String url, RequestBody body, expectations = null) throws IOException {
    String authToken = Base64.encode((USERNAME + ":" + PASSWORD).getBytes("UTF-8"))
    def request = new Request.Builder()
      .url("http://localhost:8091$url")
      .header("Authorization", "Basic " + authToken)
      .post(body)
      .build()
    def response = HTTP_CLIENT.newCall(request).execute()
    try {
      if (expectations != null) {
        if (expectations instanceof Integer) {
          assert response.code() == expectations
        } else if (expectations instanceof String) {
          assert response.body().string() == expectations
        }
      } else {
        assert response.code() == 200
      }
    } finally {
      response.close()
    }
  }

  /**
   * Copied from CouchbaseContainer.callCouchbaseRestAPI()
   */
  protected void resetBucket(BucketSettings bucketSetting) {
    try {
      // Remove existing Bucket
      if (manager.hasBucket(bucketSetting.name())) {
        manager.removeBucket(bucketSetting.name())
        assert !manager.hasBucket(bucketSetting.name())
      }

      // Calling the rest api because the library is slow and creates a lot of traces.
      callCouchbaseRestAPI("/pools/default/buckets", new FormBody.Builder()
        .add("name", bucketSetting.name())
        .add("bucketType", bucketSetting.type().name().toLowerCase())
        .add("ramQuotaMB", String.valueOf(bucketSetting.quota()))
        .add("authType", "sasl")
        .add("saslPassword", bucketSetting.password())
        .build(), 202)

      // Insert Bucket admin user
      UserSettings userSettings = UserSettings.build().password(bucketSetting.password())
        .roles([new UserRole("bucket_full_access", bucketSetting.name())])
      manager.upsertUser(AuthDomain.LOCAL, bucketSetting.name(), userSettings)

    } catch (Exception e) {
      throw new RuntimeException(e)
    }


    Bucket bucket = cluster.openBucket(bucketSetting.name(), bucketSetting.password())

    if (BucketType.COUCHBASE.equals(bucketSetting.type())) {
      try {
        bucket.query(N1qlQuery.simple(Index.dropPrimaryIndex(bucketSetting.name()),
          N1qlParams.build().consistency(ScanConsistency.REQUEST_PLUS)))

        assert bucket.query(N1qlQuery.simple(Index.createPrimaryIndex().on(bucketSetting.name()),
          N1qlParams.build().consistency(ScanConsistency.REQUEST_PLUS))).finalSuccess()

        // Create view for SpringRepository's findAll()
        bucket.bucketManager().insertDesignDocument(
          DesignDocument.create("doc", Collections.singletonList(DefaultView.create("all",
            '''
              function (doc, meta) {
                 if (doc._class == "springdata.Doc") {
                   emit(meta.id, null);
                 }
              }
              '''
          )))
        )
        TEST_WRITER.waitForTraces(13)
      } catch (Exception e) {
        throw new RuntimeException(e)
      }
    } else {
      TEST_WRITER.waitForTraces(4)
    }
    TEST_WRITER.clear() // remove traces generated by insertBucket
  }
}
