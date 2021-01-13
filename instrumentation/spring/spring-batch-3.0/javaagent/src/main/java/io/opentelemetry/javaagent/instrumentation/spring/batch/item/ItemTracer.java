package io.opentelemetry.javaagent.instrumentation.spring.batch.item;

import static io.opentelemetry.api.trace.Span.Kind.INTERNAL;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;

public class ItemTracer extends BaseTracer {
  private static final ItemTracer TRACER = new ItemTracer();

  public static ItemTracer tracer() {
    return TRACER;
  }

  // item-level listeners do not receive chunk/step context as parameters
  // fortunately the whole chunk always executes on one thread - in Spring Batch chunk is almost
  // synonymous with a DB transaction
  // that makes it quite safe to store the current chunk context in a thread local and use it in
  // each item span

  private final ThreadLocal<WeakReference<ChunkContext>> currentChunk = new ThreadLocal<>();

  public void startChunk(ChunkContext chunkContext) {
    currentChunk.set(new WeakReference<>(chunkContext));
  }

  public void endChunk() {
    currentChunk.remove();
  }

  @Nullable
  public Context startReadSpan() {
    return startItemSpan("ItemRead");
  }

  @Nullable
  public Context startProcessSpan() {
    return startItemSpan("ItemProcess");
  }

  @Nullable
  public Context startWriteSpan() {
    return startItemSpan("ItemWrite");
  }

  @Nullable
  private Context startItemSpan(String itemOperationName) {
    ChunkContext chunkContext =
        Optional.ofNullable(currentChunk.get()).map(Reference::get).orElse(null);
    if (chunkContext == null) {
      return null;
    }

    String jobName = chunkContext.getStepContext().getJobName();
    String stepName = chunkContext.getStepContext().getStepName();
    Span span =
        tracer
            .spanBuilder("BatchJob " + jobName + "." + stepName + "." + itemOperationName)
            .setSpanKind(INTERNAL)
            .startSpan();

    return Context.current().with(span);
  }

  public void end(Context context) {
    end(Span.fromContext(context));
  }

  public void endExceptionally(Context context, Throwable throwable) {
    endExceptionally(Span.fromContext(context), throwable);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-batch";
  }
}
