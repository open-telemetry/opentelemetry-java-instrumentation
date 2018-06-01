import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface EchoService extends Service {

  ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> echo();

  default Descriptor descriptor() {
    return named("echo").withCalls(
      namedCall("echo", this::echo)
    );
  }
}
