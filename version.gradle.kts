val stableVersion = "2.12.0-SNAPSHOT"
val alphaVersion = "2.12.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
