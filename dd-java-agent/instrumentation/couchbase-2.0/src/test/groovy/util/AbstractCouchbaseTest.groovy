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
class AbstractCouchbaseTest extends AgentTestRunner {

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
      println "Using provided couchbase"
    }
    manager = cluster.clusterManager(USERNAME, PASSWORD)

    if (!testBucketName.contains(AbstractCouchbaseTest.simpleName)) {
      resetBucket(cluster, bucketCouchbase)
      resetBucket(cluster, bucketMemcache)
      resetBucket(cluster, bucketEphemeral)
    }
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
    assert callCouchbaseRestAPI("/pools/default", "memoryQuota=1000&indexMemoryQuota=300") == 200
    // This one fails if already initialized, so don't assert.
    callCouchbaseRestAPI("/node/controller/setupServices", "services=kv%2Cindex%2Cn1ql%2Cfts")
    assert callCouchbaseRestAPI("/settings/web", "username=$USERNAME&password=$PASSWORD&port=8091") == 200
    assert callCouchbaseRestAPI("/settings/indexes", "indexerThreads=0&logLevel=info&maxRollbackPoints=5&storageMode=memory_optimized") == 200
  }

  /**
   * Adapted from CouchbaseContainer.callCouchbaseRestAPI()
   */
  protected int callCouchbaseRestAPI(String url, String payload) throws IOException {
    String authToken = Base64.encode((USERNAME + ":" + PASSWORD).getBytes("UTF-8"))
    def request = new Request.Builder()
      .url("http://localhost:8091$url")
      .header("Authorization", "Basic " + authToken)
      .post(RequestBody.create(FormBody.CONTENT_TYPE, payload))
      .build()
    def response = HTTP_CLIENT.newCall(request).execute()
    return response.code()
  }

  /**
   * Copied from CouchbaseContainer.callCouchbaseRestAPI()
   */
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

    bucket.query(Index.createPrimaryIndex().on(bucketSetting.name()))

    // We don't have a good way to tell that all traces are reported
    // since we don't know how many there will be.
    Thread.sleep(150)
    TEST_WRITER.clear() // remove traces generated by insertBucket

    // Create view for SpringRepository's findAll()
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
