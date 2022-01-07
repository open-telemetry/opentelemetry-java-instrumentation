plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.reactivex.rxjava3:rxjava:3.1.1")
  implementation(project(":instrumentation-api-annotation-support"))
  implementation(project(":instrumentation:rxjava:rxjava-3-common:library"))

  testImplementation(project(":instrumentation:rxjava:rxjava-3-common:testing"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dio.opentelemetry.context.enableStrictContext=false")
}
