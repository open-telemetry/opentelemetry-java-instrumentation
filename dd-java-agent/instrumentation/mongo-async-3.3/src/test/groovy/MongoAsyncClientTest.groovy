import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoClient
import com.mongodb.async.client.MongoClients
import com.mongodb.async.client.MongoCollection
import com.mongodb.async.client.MongoDatabase
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.IMongodConfig
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import io.opentracing.tag.Tags
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import spock.lang.Shared
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

@Timeout(10)
class MongoAsyncClientTest extends AgentTestRunner {

  @Shared
  MongoClient client
  @Shared
  int port = PortUtils.randomOpenPort()
  @Shared
  MongodExecutable mongodExe
  @Shared
  MongodProcess mongod

  def setup() throws Exception {
    final MongodStarter starter = MongodStarter.getDefaultInstance()
    final IMongodConfig mongodConfig =
      new MongodConfigBuilder()
        .version(Version.Main.PRODUCTION)
        .net(new Net("localhost", port, Network.localhostIsIPv6()))
        .build()

    mongodExe = starter.prepare(mongodConfig)
    mongod = mongodExe.start()

    client = MongoClients.create("mongodb://localhost:$port")
  }

  def cleanup() throws Exception {
    client?.close()
    client = null
    mongod?.stop()
    mongod = null
    mongodExe?.stop()
    mongodExe = null
  }

  def "test create collection"() {
    setup:
    MongoDatabase db = client.getDatabase(dbName)

    when:
    db.createCollection(collectionName, toCallback {})

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "{ \"create\" : \"$collectionName\", \"capped\" : \"?\" }")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test get collection"() {
    setup:
    MongoDatabase db = client.getDatabase(dbName)

    when:
    def count = new CompletableFuture()
    db.getCollection(collectionName).count toCallback { count.complete(it) }

    then:
    count.get() == 0
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "{ \"count\" : \"$collectionName\", \"query\" : { } }")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test insert"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def latch1 = new CountDownLatch(1)
      db.createCollection(collectionName, toCallback { latch1.countDown() })
      latch1.await()
      return db.getCollection(collectionName)
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    def count = new CompletableFuture()
    collection.insertOne(new Document("password", "SECRET"), toCallback {
      collection.count toCallback { count.complete(it) }
    })

    then:
    count.get() == 1
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "{ \"insert\" : \"$collectionName\", \"ordered\" : \"?\", \"documents\" : [{ \"_id\" : \"?\", \"password\" : \"?\" }] }")
      }
      trace(1, 1) {
        mongoSpan(it, 0, "{ \"count\" : \"$collectionName\", \"query\" : { } }")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test update"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def latch1 = new CountDownLatch(1)
      db.createCollection(collectionName, toCallback { latch1.countDown() })
      latch1.await()
      def coll = db.getCollection(collectionName)
      def latch2 = new CountDownLatch(1)
      coll.insertOne(new Document("password", "OLDPW"), toCallback { latch2.countDown() })
      latch2.await()
      return coll
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    def result = new CompletableFuture<UpdateResult>()
    def count = new CompletableFuture()
    collection.updateOne(
      new BsonDocument("password", new BsonString("OLDPW")),
      new BsonDocument('$set', new BsonDocument("password", new BsonString("NEWPW"))), toCallback {
      result.complete(it)
      collection.count toCallback { count.complete(it) }
    })

    then:
    result.get().modifiedCount == 1
    count.get() == 1
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "{ \"update\" : \"?\", \"ordered\" : \"?\", \"updates\" : [{ \"q\" : { \"password\" : \"?\" }, \"u\" : { \"\$set\" : { \"password\" : \"?\" } } }] }")
      }
      trace(1, 1) {
        mongoSpan(it, 0, "{ \"count\" : \"$collectionName\", \"query\" : { } }")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test delete"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def latch1 = new CountDownLatch(1)
      db.createCollection(collectionName, toCallback { latch1.countDown() })
      latch1.await()
      def coll = db.getCollection(collectionName)
      def latch2 = new CountDownLatch(1)
      coll.insertOne(new Document("password", "SECRET"), toCallback { latch2.countDown() })
      latch2.await()
      return coll
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    def result = new CompletableFuture<DeleteResult>()
    def count = new CompletableFuture()
    collection.deleteOne(new BsonDocument("password", new BsonString("SECRET")), toCallback {
      result.complete(it)
      collection.count toCallback { count.complete(it) }
    })

    then:
    result.get().deletedCount == 1
    count.get() == 0
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "{ \"delete\" : \"?\", \"ordered\" : \"?\", \"deletes\" : [{ \"q\" : { \"password\" : \"?\" }, \"limit\" : \"?\" }] }")
      }
      trace(1, 1) {
        mongoSpan(it, 0, "{ \"count\" : \"$collectionName\", \"query\" : { } }")
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  SingleResultCallback toCallback(Closure closure) {
    return new SingleResultCallback() {
      @Override
      void onResult(Object result, Throwable t) {
        if (t) {
          closure.call(t)
        } else {
          closure.call(result)
        }
      }
    }
  }

  def mongoSpan(TraceAssert trace, int index, String statement, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      serviceName "mongo"
      operationName "mongo.query"
      resourceName statement
      spanType DDSpanTypes.MONGO
      if (parentSpan == null) {
        parent()
      } else {
        childOf((DDSpan) parentSpan)
      }
      tags {
        "$Tags.COMPONENT.key" "java-mongo"
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
        "$Tags.DB_INSTANCE.key" "test_db"
        "$Tags.DB_STATEMENT.key" statement
        "$Tags.DB_TYPE.key" "mongo"
        "$Tags.PEER_HOSTNAME.key" "localhost"
        "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
        "$Tags.PEER_PORT.key" port
        defaultTags()
      }
    }
  }
}
