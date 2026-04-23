plugins {
  id("otel.java-conventions")
  id("otel.nullaway-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation("org.yaml:snakeyaml:2.6")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  implementation("io.opentelemetry:opentelemetry-sdk-common")

  testImplementation(project(":declarative-config-bridge"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
}

tasks {
  test {
    // DeclarativeConfigValidationTest walks ../instrumentation for metadata.yaml files,
    // so changes to those files must invalidate this task's build cache entry.
    inputs.files(fileTree(rootDir.resolve("instrumentation")) { include("**/metadata.yaml") })
      .withPropertyName("instrumentationMetadataYamlFiles")
      .withPathSensitivity(PathSensitivity.RELATIVE)
  }

  val runAnalysis by registering(JavaExec::class) {
    dependsOn(classes)

    systemProperty("basePath", project.rootDir)
    mainClass.set("io.opentelemetry.instrumentation.docs.DocGeneratorApplication")
    classpath(sourceSets["main"].runtimeClasspath)
  }

  val docSiteAudit by registering(JavaExec::class) {
    dependsOn(classes)

    systemProperty("basePath", project.rootDir)
    mainClass.set("io.opentelemetry.instrumentation.docs.DocSynchronization")
    classpath(sourceSets["main"].runtimeClasspath)
  }
}
