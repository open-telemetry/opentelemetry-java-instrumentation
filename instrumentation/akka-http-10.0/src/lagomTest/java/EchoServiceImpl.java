import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EchoServiceImpl implements EchoService {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> echo() {
    final CompletableFuture<Source<String, NotUsed>> fut = new CompletableFuture<>();
    ServiceTestModule.executor.submit(() -> fut.complete(Source.from(tracedMethod())));
    return req -> fut;
  }

  @Override
  public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> error() {
    throw new RuntimeException("lagom exception");
  }

  public List<String> tracedMethod() {
    TRACER.spanBuilder("tracedMethod").startSpan().end();
    return java.util.Arrays.asList("msg1", "msg2", "msg3");
  }
}
