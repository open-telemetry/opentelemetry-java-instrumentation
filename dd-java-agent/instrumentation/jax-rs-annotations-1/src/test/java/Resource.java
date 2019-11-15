import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

// Originally had this as a groovy class but was getting some weird errors.
@Path("/ignored")
public interface Resource {
  @Path("ignored")
  String hello(final String name);

  @Path("/test")
  interface SubResource extends Cloneable, Resource {
    @Override
    @POST
    @Path("/hello/{name}")
    String hello(@PathParam("name") final String name);
  }

  class Test1 implements SubResource {
    @Override
    public String hello(final String name) {
      return "Test1 " + name + "!";
    }
  }

  @Path("/test2")
  class Test2 implements SubResource {
    @Override
    public String hello(final String name) {
      return "Test2 " + name + "!";
    }
  }

  class Test3 extends Test1 {
    @Override
    public String hello(final String name) {
      return "Test3 " + name + "!";
    }
  }
}
