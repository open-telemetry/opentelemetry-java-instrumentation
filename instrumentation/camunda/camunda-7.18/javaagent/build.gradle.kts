plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.camunda.bpm")
    module.set("camunda-engine")

    // have not tested with versions prior to 7.18.0
    versions.set("[7.18.0,)")
  }
}


dependencies {
    implementation("org.camunda.bpm:camunda-engine:7.18.0")
    implementation("org.camunda.bpm:camunda-external-task-client:7.18.0")

    api("com.google.auto.value:auto-value-annotations:1.6")
    annotationProcessor("com.google.auto.value:auto-value:1.6")
}
