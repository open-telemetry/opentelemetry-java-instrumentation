import static spark.Spark.get;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;
import java.util.Arrays;
import org.bson.Document;

public class Hello {
  private static MongoDatabase mDatabase;
  private static Tracer mTracer;

  public static void main(final String[] args) {
    // Get the global tracer
    mTracer = io.opentracing.util.GlobalTracer.get();

    // initialize the Mongo database
    mDatabase = MongoDriver.getDatabase("rest_spark");

    // our routes
    get("/healthz", (req, res) -> "OK!");
    get(
        "/key/:id",
        (req, res) -> {
          try (ActiveSpan activeSpan = mTracer.buildSpan("spark.request").startActive()) {
            activeSpan.setTag("http.url", req.url());

            final String id = req.params(":id");

            // create a collection
            final Document doc =
                new Document("name", "MongoDB")
                    .append("type", "database")
                    .append("identifier", id)
                    .append("versions", Arrays.asList("v3.2", "v3.0", "v2.6"))
                    .append("info", new Document("x", 203).append("y", 102));

            final MongoCollection<Document> collection = mDatabase.getCollection("calls");
            collection.insertOne(doc);

            // write the count somewhere
            System.out.println(collection.count());

            return "Stored!";
          }
        });
  }
}
