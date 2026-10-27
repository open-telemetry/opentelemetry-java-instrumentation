plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
}

// Exercise the same consumer against published artifacts, without repository dependency substitution.
sourceSets.test {
  java.srcDir("../../../testing-common/integration-tests/src/test/java")
}

tasks.test {
  jvmArgs("-Dio.opentelemetry.context.enableStrictContext=true")
}
