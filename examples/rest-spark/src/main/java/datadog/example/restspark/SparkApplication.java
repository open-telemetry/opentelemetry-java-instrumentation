package datadog.example.restspark;

import static spark.Spark.get;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import java.util.Arrays;
import org.bson.Document;

public class SparkApplication {
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
          try (Scope scope = mTracer.buildSpan("spark.request").startActive(true)) {
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

            // add some metadata to the request Span
            scope.span().setTag("http.status_code", res.status());
            scope.span().setTag("http.url", req.url());

            return "Stored!";
          }
        });
    get(
        "/users/:id",
        (req, res) -> {
          try (Scope scope = mTracer.buildSpan("spark.request").startActive(true)) {
            // this endpoint tests the 404 decorator
            res.status(404);
            scope.span().setTag("http.status_code", res.status());
            scope.span().setTag("http.url", req.url());
          }
          return "404";
        });
  }
}
