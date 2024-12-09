plugins {
  id("otel.library-instrumentation")
}

dependencies {
    implementation("org.camunda.bpm:camunda-engine:7.18.0")
    implementation("org.camunda.bpm:camunda-external-task-client:7.18.0")

    api("com.google.auto.value:auto-value-annotations:1.6")
    annotationProcessor("com.google.auto.value:auto-value:1.6")
}

tasks.withType<Test>().configureEach {
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
}
