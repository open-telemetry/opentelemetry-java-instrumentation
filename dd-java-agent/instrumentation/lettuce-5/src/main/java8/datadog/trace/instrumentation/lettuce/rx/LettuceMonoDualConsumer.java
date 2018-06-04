package datadog.trace.instrumentation.lettuce.rx;

import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class LettuceMonoDualConsumer<R, T, U extends Throwable>
    implements Consumer<R>, BiConsumer<T, Throwable> {

  private Span span = null;
  private final Map<String, String> commandMap;
  private final boolean finishSpanOnClose;

  public LettuceMonoDualConsumer(Map<String, String> commandMap, boolean finishSpanOnClose) {
    this.commandMap = commandMap;
    this.finishSpanOnClose = finishSpanOnClose;
  }

  @Override
  public void accept(T t, Throwable throwable) {
    if (this.span != null) {
      if (throwable != null) {
        Tags.ERROR.set(this.span, true);
        this.span.log(Collections.singletonMap("error.object", throwable));
      }
      this.span.finish();
    } else {
      LoggerFactory.getLogger(Mono.class)
          .error(
              "Failed to finish this.span, BiConsumer cannot find this.span because "
                  + "it probably wasn't started.");
    }
  }

  @Override
  public void accept(R r) {
    final Scope scope =
        GlobalTracer.get()
            .buildSpan(LettuceInstrumentationUtil.SERVICE_NAME + ".query")
            .startActive(finishSpanOnClose);
    this.span = scope.span();

    Tags.DB_TYPE.set(this.span, LettuceInstrumentationUtil.SERVICE_NAME);
    Tags.SPAN_KIND.set(this.span, Tags.SPAN_KIND_CLIENT);
    Tags.COMPONENT.set(this.span, LettuceInstrumentationUtil.COMPONENT_NAME);

    this.span.setTag(
        DDTags.RESOURCE_NAME, this.commandMap.get(LettuceInstrumentationUtil.MAP_KEY_CMD_NAME));
    this.span.setTag(
        "db.command.args", this.commandMap.get(LettuceInstrumentationUtil.MAP_KEY_CMD_ARGS));
    this.span.setTag(DDTags.SERVICE_NAME, LettuceInstrumentationUtil.SERVICE_NAME);
    this.span.setTag(DDTags.SPAN_TYPE, LettuceInstrumentationUtil.SERVICE_NAME);
    scope.close();
  }
}
