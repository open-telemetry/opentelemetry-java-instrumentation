plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.reactivex.rxjava3:rxjava:3.0.12")
  implementation(project(":instrumentation-api-annotation-support"))

  testImplementation(project(":instrumentation:rxjava:rxjava-3.0:testing"))

  latestDepTestLibrary("io.reactivex.rxjava3:rxjava:3.1.0")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dio.opentelemetry.context.enableStrictContext=false")
}
