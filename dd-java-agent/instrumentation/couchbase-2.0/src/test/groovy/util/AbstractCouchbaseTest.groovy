package util

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
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment
import com.couchbase.client.java.query.Index
import com.couchbase.client.java.view.DefaultView
import com.couchbase.client.java.view.DesignDocument
import datadog.trace.agent.test.AgentTestRunner
import org.testcontainers.couchbase.CouchbaseContainer
import spock.lang.Requires
import spock.lang.Shared

import java.util.concurrent.TimeUnit

// Do not run tests locally on Java7 since testcontainers are not compatible with Java7
// It is fine to run on CI because CI provides couchbase externally, not through testcontainers
@Requires({ "true" == System.getenv("CI") || jvm.java8Compatible })
class AbstractCouchbaseTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.couchbase.enabled", "true")
  }
  private static final USERNAME = "Administrator"
  private static final PASSWORD = "password"

  static final BUCKET_COUCHBASE = DefaultBucketSettings.builder()
    .enableFlush(true)
    .name("test-bucket-cb")
    .password("test-pass")
    .type(BucketType.COUCHBASE)
    .quota(100)
    .build()

  static final BUCKET_MEMCACHE = DefaultBucketSettings.builder()
    .enableFlush(true)
    .name("test-bucket-mem")
    .password("test-pass")
    .type(BucketType.MEMCACHED)
    .quota(100)
    .build()

  static final BUCKET_EPHEMERAL = DefaultBucketSettings.builder()
    .enableFlush(true)
    .name("test-bucket-emp")
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
  CouchbaseCluster cluster
  @Shared
  ClusterManager manager

  def setupSpec() {

    /*
      CI will provide us with couchbase container running along side our build.
      When building locally, however, we need to take matters into our own hands
      and we use 'testcontainers' for this.
     */
    if ("true" != System.getenv("CI")) {
      couchbaseContainer = new CouchbaseContainer(
        clusterUsername: USERNAME,
        clusterPassword: PASSWORD)
      couchbaseContainer.start()
      cluster = couchbaseContainer.getCouchbaseCluster()
      println "Couchbase container started"
    } else {
      initCluster()
      cluster = CouchbaseCluster.create(envBuilder().build())
      println "Using local couchbase"
    }
    manager = cluster.clusterManager(USERNAME, PASSWORD)

    resetBucket(cluster, BUCKET_COUCHBASE)
    resetBucket(cluster, BUCKET_MEMCACHE)
    resetBucket(cluster, BUCKET_EPHEMERAL)
  }

  def cleanupSpec() {
    // Close all buckets and disconnect
    cluster.disconnect()

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
    assert callCouchbaseRestAPI("/pools/default", "memoryQuota=600&indexMemoryQuota=300") == 200
    // This one fails if already initialized, so don't assert.
    callCouchbaseRestAPI("/node/controller/setupServices", "services=kv%2Cn1ql%2Cindex%2Cfts")
    assert callCouchbaseRestAPI("/settings/web", "username=$USERNAME&password=$PASSWORD&port=8091") == 200
//      callCouchbaseRestAPI(bucketURL, sampleBucketPayloadBuilder.toString())
    assert callCouchbaseRestAPI("/settings/indexes", "indexerThreads=0&logLevel=info&maxRollbackPoints=5&storageMode=memory_optimized") == 200
  }

  protected int callCouchbaseRestAPI(String url, String payload) throws IOException {
    String fullUrl = "http://localhost:8091" + url
    HttpURLConnection httpConnection = (HttpURLConnection) ((new URL(fullUrl).openConnection()))
    try {
      httpConnection.setDoOutput(true)
      httpConnection.setRequestMethod("POST")
      httpConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      String encoded = Base64.encode((USERNAME + ":" + PASSWORD).getBytes("UTF-8"))
      httpConnection.setRequestProperty("Authorization", "Basic " + encoded)
      DataOutputStream out = new DataOutputStream(httpConnection.getOutputStream())
      try {
        out.writeBytes(payload)
        out.flush()
        def code = httpConnection.getResponseCode()
        return code
      } finally {
        if (Collections.singletonList(out).get(0) != null) {
          out.close()
        }
      }
    } finally {
      if (Collections.singletonList(httpConnection).get(0) != null) {
        httpConnection.disconnect()
      }
    }
  }

  protected void resetBucket(CouchbaseCluster cluster, BucketSettings bucketSetting) {
    ClusterManager clusterManager = cluster.clusterManager(USERNAME, PASSWORD)

    // Remove existing Bucket
    if (clusterManager.hasBucket(bucketSetting.name())) {
      clusterManager.removeBucket(bucketSetting.name())
    }
    assert !clusterManager.hasBucket(bucketSetting.name())

    // Insert Bucket... This generates a LOT of traces
    BucketSettings bucketSettings = clusterManager.insertBucket(bucketSetting)
    // Insert Bucket admin user
    UserSettings userSettings = UserSettings.build().password(bucketSetting.password())
      .roles([new UserRole("bucket_full_access", bucketSetting.name())])

    clusterManager.upsertUser(AuthDomain.LOCAL, bucketSetting.name(), userSettings)

    Bucket bucket = cluster.openBucket(bucketSettings.name(), bucketSettings.password())

//    boolean queryServiceEnabled = false
//    while (!queryServiceEnabled) {
//      GetClusterConfigResponse clusterConfig = bucket.core().<GetClusterConfigResponse> send(new GetClusterConfigRequest()).toBlocking().single()
//      queryServiceEnabled = clusterConfig.config().bucketConfig(bucket.name()).serviceEnabled(ServiceType.QUERY)
//    }
    bucket.query(Index.createPrimaryIndex().on(bucketSetting.name()))

    TEST_WRITER.clear() // remove traces generated by insertBucket
    if (BucketType.COUCHBASE.equals(bucketSettings.type())) {
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
    }
  }
}
