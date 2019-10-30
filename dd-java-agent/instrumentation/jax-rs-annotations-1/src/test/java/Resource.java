import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

// Originally had this as a groovy class but was getting some weird errors.
@Path("/ignored")
public interface Resource {

  @Path("/test")
  class Test implements Resource {
    @POST
    @Path("/hello/{name}")
    public String addBook(@PathParam("name") final String name) {
      return "Hello " + name + "!";
    }
  }
}
