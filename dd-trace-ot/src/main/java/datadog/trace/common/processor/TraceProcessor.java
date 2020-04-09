package datadog.trace.common.processor;

import datadog.opentracing.DDSpan;
import datadog.trace.api.Config;
import datadog.trace.common.processor.rule.AnalyticsSampleRateRule;
import datadog.trace.common.processor.rule.DBStatementRule;
import datadog.trace.common.processor.rule.ErrorRule;
import datadog.trace.common.processor.rule.HttpStatusErrorRule;
import datadog.trace.common.processor.rule.ResourceNameRule;
import datadog.trace.common.processor.rule.SpanTypeRule;
import datadog.trace.common.processor.rule.Status404Rule;
import datadog.trace.common.processor.rule.URLAsResourceNameRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TraceProcessor {
  final Rule[] DEFAULT_RULES =
      new Rule[] {
        // Rules are applied in order.
        new DBStatementRule(),
        new ResourceNameRule(),
        new SpanTypeRule(),
        new HttpStatusErrorRule(),
        new ErrorRule(),
        new URLAsResourceNameRule(),
        new Status404Rule(),
        new AnalyticsSampleRateRule(),
      };

  private final List<Rule> rules;

  public TraceProcessor() {

    rules = new ArrayList<>(DEFAULT_RULES.length);
    for (final Rule rule : DEFAULT_RULES) {
      if (isEnabled(rule)) {
        rules.add(rule);
      }
    }
  }

  private static boolean isEnabled(final Rule rule) {
    boolean enabled = Config.get().isRuleEnabled(rule.getClass().getSimpleName());
    for (final String alias : rule.aliases()) {
      enabled &= Config.get().isRuleEnabled(alias);
    }
    if (!enabled) {
      log.debug("{} disabled", rule.getClass().getSimpleName());
    }
    return enabled;
  }

  public interface Rule {
    String[] aliases();

    void processSpan(DDSpan span, Map<String, Object> tags, Collection<DDSpan> trace);
  }

  public List<DDSpan> onTraceComplete(final List<DDSpan> trace) {
    for (final DDSpan span : trace) {
      applyRules(trace, span);
    }

    // TODO: apply DDTracer's TraceInterceptors
    return trace;
  }

  private void applyRules(final Collection<DDSpan> trace, final DDSpan span) {
    final Map<String, Object> tags = span.getTags();
    for (final Rule rule : rules) {
      rule.processSpan(span, tags, trace);
    }
  }
}
