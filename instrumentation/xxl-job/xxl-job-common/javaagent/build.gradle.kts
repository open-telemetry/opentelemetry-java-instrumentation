plugins {
  id("otel.javaagent-instrumentation")
}
dependencies {
  compileOnly("com.xuxueli:xxl-job-core:2.1.2")
  bootstrap(project(":instrumentation:xxl-job:xxl-job-common:bootstrap"))
}
