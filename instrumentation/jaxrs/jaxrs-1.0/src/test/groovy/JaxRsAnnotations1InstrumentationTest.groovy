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

import io.opentelemetry.auto.bootstrap.WeakMap
import io.opentelemetry.auto.instrumentation.jaxrs.v1_0.JaxRsAnnotationsDecorator
import io.opentelemetry.auto.test.AgentTestRunner

import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.HEAD
import javax.ws.rs.OPTIONS
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import java.lang.reflect.Method

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

class JaxRsAnnotations1InstrumentationTest extends AgentTestRunner {

  def "instrumentation can be used as root span and resource is set to METHOD PATH"() {
    setup:
    new Jax() {
      @POST
      @Path("/a")
      void call() {
      }
    }.call()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "POST /a"
          tags {
          }
        }
      }
    }
  }

  def "span named '#name' from annotations on class when is not root span"() {
    setup:
    def startingCacheSize = spanNames.size()
    runUnderTrace("test") {
      obj.call()
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName name
          parent()
          tags {
          }
        }
        span(1) {
          operationName "${className}.call"
          childOf span(0)
          tags {
          }
        }
      }
    }
    spanNames.size() == startingCacheSize + 1
    spanNames.get(obj.class).size() == 1

    when: "multiple calls to the same method"
    runUnderTrace("test") {
      (1..10).each {
        obj.call()
      }
    }
    then: "doesn't increase the cache size"
    spanNames.size() == startingCacheSize + 1
    spanNames.get(obj.class).size() == 1

    where:
    name                 | obj
    "/a"                 | new Jax() {
      @Path("/a")
      void call() {
      }
    }
    "GET /b"             | new Jax() {
      @GET
      @Path("/b")
      void call() {
      }
    }
    "POST /interface/c"  | new InterfaceWithPath() {
      @POST
      @Path("/c")
      void call() {
      }
    }
    "HEAD /interface"    | new InterfaceWithPath() {
      @HEAD
      void call() {
      }
    }
    "POST /abstract/d"   | new AbstractClassWithPath() {
      @POST
      @Path("/d")
      void call() {
      }
    }
    "PUT /abstract"      | new AbstractClassWithPath() {
      @PUT
      void call() {
      }
    }
    "OPTIONS /child/e"   | new ChildClassWithPath() {
      @OPTIONS
      @Path("/e")
      void call() {
      }
    }
    "DELETE /child/call" | new ChildClassWithPath() {
      @DELETE
      void call() {
      }
    }
    "POST /child/call"   | new ChildClassWithPath()
    "GET /child/call"    | new JavaInterfaces.ChildClassOnInterface()
    // TODO: uncomment when we drop support for Java 7
    // "GET /child/invoke"  | new JavaInterfaces.DefaultChildClassOnInterface()

    className = getClassName(obj.class)

    // JavaInterfaces classes are loaded on a different classloader, so we need to find the right cache instance.
    decorator = obj.class.classLoader.loadClass(JaxRsAnnotationsDecorator.name).getField("DECORATE").get(null)
    spanNames = (WeakMap<Class, Map<Method, String>>) decorator.spanNames
  }

  def "no annotations has no effect"() {
    setup:
    runUnderTrace("test") {
      obj.call()
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "test"
          tags {
          }
        }
      }
    }

    where:
    obj | _
    new Jax() {
      void call() {
      }
    }   | _
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

  @Path("child")
  class ChildClassWithPath extends AbstractClassWithPath {
    @Path("call")
    @POST
    void call() {
    }
  }
}
