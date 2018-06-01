import static java.util.concurrent.CompletableFuture.completedFuture;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import akka.NotUsed;
import akka.stream.javadsl.Source;
import datadog.trace.api.Trace;

import java.util.List;

public class EchoServiceImpl implements EchoService {

  @Override
  public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> echo() {
    return req -> completedFuture(Source.from(tracedMethod()));
  }

  @Trace
  public List<String> tracedMethod() {
    return java.util.Arrays.asList("msg1", "msg2", "msg3");
  }
}
