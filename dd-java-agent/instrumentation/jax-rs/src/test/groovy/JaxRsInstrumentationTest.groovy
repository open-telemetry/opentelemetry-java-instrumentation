import datadog.opentracing.DDSpanContext
import datadog.trace.agent.test.AgentTestRunner
import io.opentracing.util.GlobalTracer
import spock.lang.Unroll

import javax.ws.rs.*

class JaxRsInstrumentationTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jax-rs.enabled", "true")
  }

  @Unroll
  def "span named '#resourceName' from annotations on class"() {
    setup:
    def scope = GlobalTracer.get().buildSpan("test").startActive(false)
    DDSpanContext spanContext = scope.span().context()
    obj.call()

    expect:
    spanContext.resourceName == resourceName

    cleanup:
    scope.close()

    where:
    resourceName                | obj
    "test"                      | new Jax() {
      // invalid because no annotations
      void call() {}
    }
    "/a"                        | new Jax() {
      @Path("/a")
      void call() {}
    }
    "GET /b"                    | new Jax() {
      @GET
      @Path("/b")
      void call() {}
    }
    "test"                      | new InterfaceWithPath() {
      // invalid because no annotations
      void call() {}
    }
    "POST /c"                   | new InterfaceWithPath() {
      @POST
      @Path("/c")
      void call() {}
    }
    "HEAD"                      | new InterfaceWithPath() {
      @HEAD
      void call() {}
    }
    "test"                      | new AbstractClassWithPath() {
      // invalid because no annotations
      void call() {}
    }
    "POST /abstract/d"          | new AbstractClassWithPath() {
      @POST
      @Path("/d")
      void call() {}
    }
    "PUT /abstract"             | new AbstractClassWithPath() {
      @PUT
      void call() {}
    }
    "test"                      | new ChildClassWithPath() {
      // invalid because no annotations
      void call() {}
    }
    "OPTIONS /abstract/child/e" | new ChildClassWithPath() {
      @OPTIONS
      @Path("/e")
      void call() {}
    }
    "DELETE /abstract/child"    | new ChildClassWithPath() {
      @DELETE
      void call() {}
    }
    "POST /abstract/child"      | new ChildClassWithPath()
  }

  interface Jax {
    void call()
  }

  @Path("/interface")
  interface InterfaceWithPath extends Jax {
    @GET
    void call()
  }

  @Path("/abstract")
  abstract class AbstractClassWithPath implements Jax {
    @PUT
    abstract void call()
  }

  @Path("/child")
  class ChildClassWithPath extends AbstractClassWithPath {
    @POST
    void call() {}
  }
}
