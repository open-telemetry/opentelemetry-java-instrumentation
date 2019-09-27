import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

// Originally had this as a groovy class but was getting some weird errors.
@Path("/test")
public class TestResource {
  @POST
  @Path("/hello/{name}")
  public String addBook(@PathParam("name") final String name) {
    return "Hello " + name + "!";
  }
}
