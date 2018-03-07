import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

public class TestSparkJavaApplication {

  public static void main(final String[] args) {

    Spark.get(
        "/",
        new Route() {
          @Override
          public Object handle(Request request, Response response) throws Exception {
            return "Hello World";
          }
        });

    Spark.get(
        "/param/:param",
        new Route() {
          @Override
          public Object handle(Request request, Response response) throws Exception {
            return "Hello " + request.params("param");
          }
        });

    Spark.get(
        "/exception/:param",
        new Route() {
          @Override
          public Object handle(Request request, Response response) throws Exception {
            throw new RuntimeException(request.params("param"));
          }
        });
  }
}
