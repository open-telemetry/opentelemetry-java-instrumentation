import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.namedCall;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;

public interface EchoService extends Service {

  ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> echo();

  ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> error();

  @Override
  default Descriptor descriptor() {
    return named("echo").withCalls(namedCall("echo", this::echo), namedCall("error", this::error));
  }
}
