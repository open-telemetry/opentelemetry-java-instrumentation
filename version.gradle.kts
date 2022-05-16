val stableVersion = "1.15.0-SNAPSHOT-SNAPSHOT"
val alphaVersion = "1.15.0-SNAPSHOT-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
