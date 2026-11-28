/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.benchmark.webflux;

import com.sun.management.OperatingSystemMXBean;
import io.micrometer.context.ContextRegistry;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication
public class WebFluxApplication {
  private static final String REQUEST_CONTEXT_KEY = "benchmark.request-context";
  private static final ThreadLocal<String> REQUEST_CONTEXT = new ThreadLocal<>();

  public static void main(String[] args) {
    ContextRegistry.getInstance().registerThreadLocalAccessor(REQUEST_CONTEXT_KEY, REQUEST_CONTEXT);
    if (Boolean.getBoolean("benchmark.reactor.automatic")) {
      Hooks.enableAutomaticContextPropagation();
    }
    SpringApplication.run(WebFluxApplication.class, args);
  }

  @RestController
  static class Owners {
    private final DatabaseClient database;

    Owners(DatabaseClient database) {
      this.database = database;
    }

    @GetMapping("/owners/{id}")
    Mono<OwnerResponse> owner(
        @PathVariable("id") int id, @RequestHeader("X-Request-Id") String requestId) {
      return readOwner(id, requestId, OwnerResponse::new);
    }

    @GetMapping("/probe")
    Mono<Probe> probe() throws ReflectiveOperationException {
      // Read the installed hook rather than the system property used to configure the agent.
      var getHooks = Hooks.class.getDeclaredMethod("getOnEachOperatorHooks");
      getHooks.setAccessible(true);
      boolean agentHook =
          ((Map<?, ?>) getHooks.invoke(null))
              .keySet().stream()
                  .anyMatch(key -> key.toString().endsWith(".reactor.v3_1.TracingSubscriber"));
      return readOwner(
          1,
          "probe",
          (owner, requestId) -> new Probe(owner, requestId, REQUEST_CONTEXT.get(), agentHook));
    }

    private <T> Mono<T> readOwner(int id, String requestId, BiFunction<Owner, String, T> response) {
      return Mono.deferContextual(context ->
              database
                  .sql("SELECT id, first_name, last_name FROM owners WHERE id = :id")
                  .bind("id", id)
                  .map((row, metadata) ->
                      new Owner(
                          row.get("id", Integer.class),
                          row.get("first_name", String.class),
                          row.get("last_name", String.class)))
                  .one()
                  .publishOn(Schedulers.parallel())
                  .map(owner -> response.apply(owner, context.get(REQUEST_CONTEXT_KEY))))
          .contextWrite(context -> context.put(REQUEST_CONTEXT_KEY, requestId));
    }

    @GetMapping("/cpu")
    CpuSnapshot cpu() {
      var os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
      return new CpuSnapshot(
          os.getProcessCpuTime(), ManagementFactory.getRuntimeMXBean().getUptime());
    }
  }

  record Owner(int id, String firstName, String lastName) {}

  record OwnerResponse(Owner owner, String requestId) {}

  record Probe(Owner owner, String requestId, String threadLocal, boolean agentHook) {}

  record CpuSnapshot(long processCpuNanos, long uptimeMillis) {}
}
