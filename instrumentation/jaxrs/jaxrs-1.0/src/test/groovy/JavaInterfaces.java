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
import javax.ws.rs.GET;
import javax.ws.rs.Path;

public class JavaInterfaces {

  interface Jax {

    void call();
  }

  @Path("interface")
  interface InterfaceWithClassMethodPath extends Jax {

    @Override
    @GET
    @Path("invoke")
    void call();
  }

  @Path("abstract")
  abstract class AbstractClassOnInterfaceWithClassPath implements InterfaceWithClassMethodPath {

    @GET
    @Path("call")
    @Override
    public void call() {
      // do nothing
    }

    abstract void actual();
  }

  @Path("child")
  class ChildClassOnInterface extends AbstractClassOnInterfaceWithClassPath {

    @Override
    void actual() {
      // do nothing
    }
  }

  // TODO: uncomment when we drop support for Java 7
  //  @Path("interface")
  //  interface DefaultInterfaceWithClassMethodPath extends Jax {
  //
  //    @GET
  //    @Path("invoke")
  //    default void call() {
  //      actual();
  //    }
  //
  //    void actual();
  //  }
  //
  //  @Path("child")
  //  class DefaultChildClassOnInterface implements DefaultInterfaceWithClassMethodPath {
  //
  //    @Override
  //    public void actual() {
  //      // do nothing
  //    }
  //  }
}
