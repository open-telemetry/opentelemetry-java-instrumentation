package datadog.trace.instrumentation.lettuce.rx;

import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.function.Consumer;
import org.reactivestreams.Subscription;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;

public class LettuceFluxTerminationRunnable implements Consumer<Signal>, Runnable {

  private Span span = null;
  private int numResults = 0;
  private FluxOnSubscribeConsumer onSubscribeConsumer = null;

  public LettuceFluxTerminationRunnable(String commandName, boolean finishSpanOnClose) {
    this.onSubscribeConsumer = new FluxOnSubscribeConsumer(this, commandName, finishSpanOnClose);
  }

  public FluxOnSubscribeConsumer getOnSubscribeConsumer() {
    return onSubscribeConsumer;
  }

  private void finishSpan(boolean isCommandCancelled, Throwable throwable) {
    if (this.span != null) {
      this.span.setTag("db.command.results.count", this.numResults);
      if (isCommandCancelled) {
        this.span.setTag("db.command.cancelled", true);
      }
      if (throwable != null) {
        Tags.ERROR.set(this.span, true);
        this.span.log(Collections.singletonMap("error.object", throwable));
      }
      this.span.finish();
    } else {
      LoggerFactory.getLogger(Flux.class)
          .error(
              "Failed to finish this.span, LettuceFluxTerminationRunnable cannot find this.span because "
                  + "it probably wasn't started.");
    }
  }

  @Override
  public void accept(Signal signal) {
    if (SignalType.ON_COMPLETE.equals(signal.getType())
        || SignalType.ON_ERROR.equals(signal.getType())) {
      finishSpan(false, signal.getThrowable());
    } else if (SignalType.ON_NEXT.equals(signal.getType())) {
      ++this.numResults;
    }
  }

  @Override
  public void run() {
    if (this.span != null) {
      finishSpan(true, null);
    } else {
      LoggerFactory.getLogger(Flux.class)
          .error(
              "Failed to finish this.span to indicate cancellation, LettuceFluxTerminationRunnable cannot find this.span because "
                  + "it probably wasn't started.");
    }
  }

  public static class FluxOnSubscribeConsumer implements Consumer<Subscription> {

    private final LettuceFluxTerminationRunnable owner;
    private final String commandName;
    private final boolean finishSpanOnClose;

    public FluxOnSubscribeConsumer(
        LettuceFluxTerminationRunnable owner, String commandName, boolean finishSpanOnClose) {
      this.owner = owner;
      this.commandName = commandName;
      this.finishSpanOnClose = finishSpanOnClose;
    }

    @Override
    public void accept(Subscription subscription) {
      final Scope scope =
          GlobalTracer.get()
              .buildSpan(LettuceInstrumentationUtil.SERVICE_NAME + ".query")
              .startActive(finishSpanOnClose);
      final Span span = scope.span();
      this.owner.span = span;

      Tags.DB_TYPE.set(span, LettuceInstrumentationUtil.SERVICE_NAME);
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
      Tags.COMPONENT.set(span, LettuceInstrumentationUtil.COMPONENT_NAME);

      // should be command name only, but use workaround to prepend string to agent crashing commands
      span.setTag(
          DDTags.RESOURCE_NAME,
          LettuceInstrumentationUtil.getCommandResourceName(this.commandName));
      span.setTag(DDTags.SERVICE_NAME, LettuceInstrumentationUtil.SERVICE_NAME);
      span.setTag(DDTags.SPAN_TYPE, LettuceInstrumentationUtil.SERVICE_NAME);
      scope.close();
    }
  }
}
