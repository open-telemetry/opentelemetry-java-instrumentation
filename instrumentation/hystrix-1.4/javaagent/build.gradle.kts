plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.netflix.hystrix")
    module.set("hystrix-core")
    versions.set("[1.4.0,)")
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:rxjava:rxjava-1.0:library"))

  library("com.netflix.hystrix:hystrix-core:1.4.0")
  library("io.reactivex:rxjava:1.0.8")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.hystrix.experimental-span-attributes=true")
  // Disable so failure testing below doesn't inadvertently change the behavior.
  jvmArgs("-Dhystrix.command.default.circuitBreaker.enabled=false")
  jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")

  // Uncomment for debugging:
  // jvmArgs("-Dhystrix.command.default.execution.timeout.enabled=false")
}
