import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import datadog.trace.api.Trace;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class EchoServiceImpl implements EchoService {

  @Override
  public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> echo() {
    final CompletableFuture<Source<String, NotUsed>> fut = new CompletableFuture<>();
    ServiceTestModule.executor.submit(
      // FIXME: we cannot use lambda here due to BB/JVM bug and RunnableInstrumentation being present.
      // See https://github.com/raphw/byte-buddy/issues/558. Once that is fixed we can make this lambda again.
      // Technically this doesn't return object but we do not really care here
      new Callable<Object>() {

        @Override
        public Object call() throws Exception {
          return fut.complete(Source.from(tracedMethod()));
        }
      });
    return req -> fut;
  }

  @Override
  public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> error() {
    throw new RuntimeException("lagom exception");
  }

  @Trace
  public List<String> tracedMethod() {
    return java.util.Arrays.asList("msg1", "msg2", "msg3");
  }

}
