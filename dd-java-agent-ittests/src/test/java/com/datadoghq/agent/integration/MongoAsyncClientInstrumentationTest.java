package com.datadoghq.agent.integration;

import static com.datadoghq.agent.integration.MongoClientInstrumentationTest.MONGO_DB_NAME;
import static com.datadoghq.agent.integration.MongoClientInstrumentationTest.MONGO_HOST;
import static com.datadoghq.agent.integration.MongoClientInstrumentationTest.MONGO_PORT;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.writer.ListWriter;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoDatabase;
import dd.test.TestUtils;
import io.opentracing.tag.Tags;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class MongoAsyncClientInstrumentationTest {
  private static MongoClient client;
  private static final ListWriter writer = new ListWriter();
  private static final DDTracer tracer = new DDTracer(writer);

  @BeforeClass
  public static void setup() throws Exception {
    TestUtils.registerOrReplaceGlobalTracer(tracer);
    MongoClientInstrumentationTest.startLocalMongo();
    client = MongoClients.create("mongodb://" + MONGO_HOST + ":" + MONGO_PORT);
  }

  @AfterClass
  public static void destroy() throws Exception {
    if (null != client) {
      client.close();
      client = null;
    }
    MongoClientInstrumentationTest.stopLocalMongo();
  }

  @Test
  public void asyncClientHasListener() {
    Assert.assertEquals(1, client.getSettings().getCommandListeners().size());
    Assert.assertEquals(
        "DDTracingCommandListener",
        client.getSettings().getCommandListeners().get(0).getClass().getSimpleName());
  }

  @Test
  public void insertOperation() throws InterruptedException, Exception {
    final MongoDatabase db = client.getDatabase(MONGO_DB_NAME);
    final String collectionName = "asyncCollection";
    final AtomicBoolean done = new AtomicBoolean(false);

    db.createCollection(
        collectionName,
        new SingleResultCallback<Void>() {
          @Override
          public void onResult(final Void result, final Throwable t) {
            done.set(true);
          }
        });
    while (!done.get()) {
      Thread.sleep(1);
    }

    db.getCollection(collectionName)
        .insertOne(
            new Document("foo", "bar"),
            new SingleResultCallback<Void>() {
              @Override
              public void onResult(final Void result, final Throwable t) {
                done.set(true);
              }
            });
    while (!done.get()) {
      Thread.sleep(1);
    }

    done.set(false);
    db.getCollection(collectionName)
        .count(
            new SingleResultCallback<Long>() {
              @Override
              public void onResult(final Long result, final Throwable t) {
                Assert.assertEquals(1, result.longValue());
                done.set(true);
              }
            });

    while (!done.get()) {
      Thread.sleep(1);
    }

    // the final trace may still be reporting to the ListWriter,
    // but we're only testing the first trace.
    Assert.assertTrue(writer.getList().size() >= 1);

    final String createCollectionQuery =
        "{ \"create\" : \"asyncCollection\", \"autoIndexId\" : \"?\", \"capped\" : \"?\" }";
    final DDBaseSpan<?> trace0 = writer.get(0).get(0);
    Assert.assertEquals("mongo.query", trace0.getOperationName());
    Assert.assertEquals(createCollectionQuery, trace0.getResourceName());
    Assert.assertEquals("mongodb", trace0.getType());
    Assert.assertEquals("mongo", trace0.getServiceName());

    Assert.assertEquals("java-mongo", trace0.getTags().get(Tags.COMPONENT.getKey()));
    Assert.assertEquals(createCollectionQuery, trace0.getTags().get(Tags.DB_STATEMENT.getKey()));
    Assert.assertEquals(MONGO_DB_NAME, trace0.getTags().get(Tags.DB_INSTANCE.getKey()));
    Assert.assertEquals(MONGO_HOST, trace0.getTags().get(Tags.PEER_HOSTNAME.getKey()));
    Assert.assertEquals(
        ByteBuffer.wrap(InetAddress.getByName("127.0.0.1").getAddress()).getInt(),
        trace0.getTags().get(Tags.PEER_HOST_IPV4.getKey()));
    Assert.assertEquals(MONGO_PORT, trace0.getTags().get(Tags.PEER_PORT.getKey()));
    Assert.assertEquals("mongo", trace0.getTags().get(Tags.DB_TYPE.getKey()));
  }
}
