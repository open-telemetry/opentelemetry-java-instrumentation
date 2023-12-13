import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.javaagent-instrumentation")
  id("io.opentelemetry.instrumentation.javaagent-shadowing")
}

dependencies {
  compileOnly(project(":javaagent-bootstrap"))
  compileOnly(project(":javaagent-tooling"))
  compileOnly(project(":javaagent-extension-api"))
  compileOnly(project(":instrumentation-api"))
  compileOnly(project(":instrumentation-annotations-support"))
  compileOnly(project(":instrumentation:internal:internal-reflection:javaagent"))
  compileOnly(project(":instrumentation:executors:bootstrap"))
  compileOnly(project(":instrumentation:java-util-logging:javaagent"))
  compileOnly(project(":instrumentation:java-util-logging:shaded-stub-for-instrumenting"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  // Dependent on the modifications in https://github.com/oracle/graal/pull/8077. Use a developing snapshot for now
  compileOnly("org.graalvm.sdk:graal-sdk:23.0.2-instru")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  val shadowJar by existing(ShadowJar::class) {
    archiveFileName.set("${project.name}-${project.version}.jar")
  }

  assemble {
    dependsOn(shadowJar)
  }
}
