import static com.lightbend.lagom.javadsl.api.Service.*;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.*;

public interface EchoService extends Service {

  ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> echo();

  ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> error();

  @Override
  default Descriptor descriptor() {
    return named("echo").withCalls(namedCall("echo", this::echo), namedCall("error", this::error));
  }
}
