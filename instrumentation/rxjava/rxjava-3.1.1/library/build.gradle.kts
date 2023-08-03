plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.rxjava.v3_1_1")

dependencies {
  library("io.reactivex.rxjava3:rxjava:3.1.1")
  implementation(project(":instrumentation-annotations-support"))
  implementation(project(":instrumentation:rxjava:rxjava-3-common:library"))

  testImplementation(project(":instrumentation:rxjava:rxjava-3-common:testing"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dio.opentelemetry.context.enableStrictContext=false")
}
