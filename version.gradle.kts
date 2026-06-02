val stableVersion = "2.29.0-SNAPSHOT"
val alphaVersion = "2.29.0-alpha-SNAPSHOT"

val apidiffBaselineVersion = "2.28.1"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
