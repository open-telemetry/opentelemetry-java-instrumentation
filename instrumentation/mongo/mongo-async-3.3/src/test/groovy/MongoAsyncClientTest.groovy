/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.mongodb.ConnectionString
import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoClient
import com.mongodb.async.client.MongoClientSettings
import com.mongodb.async.client.MongoClients
import com.mongodb.async.client.MongoCollection
import com.mongodb.async.client.MongoDatabase
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.connection.ClusterSettings
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import spock.lang.Shared
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

@Timeout(10)
class MongoAsyncClientTest extends MongoBaseTest {

  @Shared
  MongoClient client

  def setup() throws Exception {
    client = MongoClients.create(
      MongoClientSettings.builder()
        .clusterSettings(
          ClusterSettings.builder()
            .description("some-description")
            .applyConnectionString(new ConnectionString("mongodb://localhost:$port"))
            .build())
        .build())
  }

  def cleanup() throws Exception {
    client?.close()
    client = null
  }

  def "test create collection"() {
    setup:
    MongoDatabase db = client.getDatabase(dbName)

    when:
    db.createCollection(collectionName, toCallback {})

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0) {
          assert it.replaceAll(" ", "") == "{\"create\":\"$collectionName\",\"capped\":\"?\"}" ||
            it == "{\"create\": \"$collectionName\", \"capped\": \"?\", \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test create collection no description"() {
    setup:
    MongoDatabase db = MongoClients.create("mongodb://localhost:$port").getDatabase(dbName)

    when:
    db.createCollection(collectionName, toCallback {})

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, {
          assert it.replaceAll(" ", "") == "{\"create\":\"$collectionName\",\"capped\":\"?\"}" ||
            it == "{\"create\": \"$collectionName\", \"capped\": \"?\", \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }, dbName)
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
        mongoSpan(it, 0) {
          assert it.replaceAll(" ", "") == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
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
        mongoSpan(it, 0) {
          assert it.replaceAll(" ", "") == "{\"insert\":\"$collectionName\",\"ordered\":\"?\",\"documents\":[{\"_id\":\"?\",\"password\":\"?\"}]}" ||
            it == "{\"insert\": \"$collectionName\", \"ordered\": \"?\", \"\$db\": \"?\", \"documents\": [{\"_id\": \"?\", \"password\": \"?\"}]}"
          true
        }
      }
      trace(1, 1) {
        mongoSpan(it, 0) {
          assert it.replaceAll(" ", "") == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
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
        mongoSpan(it, 0) {
          assert it.replaceAll(" ", "") == "{\"update\":\"?\",\"ordered\":\"?\",\"updates\":[{\"q\":{\"password\":\"?\"},\"u\":{\"\$set\":{\"password\":\"?\"}}}]}" ||
            it == "{\"update\": \"?\", \"ordered\": \"?\", \"\$db\": \"?\", \"updates\": [{\"q\": {\"password\": \"?\"}, \"u\": {\"\$set\": {\"password\": \"?\"}}}]}"
          true
        }
      }
      trace(1, 1) {
        mongoSpan(it, 0) {
          assert it.replaceAll(" ", "") == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
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
        mongoSpan(it, 0) {
          assert it.replaceAll(" ", "") == "{\"delete\":\"?\",\"ordered\":\"?\",\"deletes\":[{\"q\":{\"password\":\"?\"},\"limit\":\"?\"}]}" ||
            it == "{\"delete\": \"?\", \"ordered\": \"?\", \"\$db\": \"?\", \"deletes\": [{\"q\": {\"password\": \"?\"}, \"limit\": \"?\"}]}"
          true
        }
      }
      trace(1, 1) {
        mongoSpan(it, 0) {
          assert it.replaceAll(" ", "") == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
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

  def mongoSpan(TraceAssert trace, int index, Closure<Boolean> statementEval, String instance = "some-description", Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      operationName statementEval
      spanKind CLIENT
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      tags {
        "$MoreTags.NET_PEER_NAME" "localhost"
        "$MoreTags.NET_PEER_IP" "127.0.0.1"
        "$MoreTags.NET_PEER_PORT" port
        "$Tags.DB_URL" "mongodb://localhost:" + port
        "$Tags.DB_STATEMENT" statementEval
        "$Tags.DB_TYPE" "mongo"
        "$Tags.DB_INSTANCE" instance
      }
    }
  }
}
