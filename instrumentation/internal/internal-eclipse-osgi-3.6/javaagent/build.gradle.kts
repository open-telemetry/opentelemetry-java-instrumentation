plugins {
  id("otel.javaagent-instrumentation")
}

// this instrumentation applies to the class 'org.eclipse.osgi.internal.loader.BundleLoader'
// which is present in the following artifacts dating back to version 3.6 (2010):
//
// * 'org.eclipse.platform:org.eclipse.osgi'
// * 'org.eclipse.tycho:org.eclipse.osgi'
// * 'org.eclipse.osgi:org.eclipse.osgi'

// TODO write a smoke test that does the following:
//
//  docker run --mount 'type=bind,src=$AGENT_PATH,dst=/opentelemetry-javaagent.jar'
//             -e JAVA_TOOL_OPTIONS=-javaagent:/opentelemetry-javaagent.jar
//             wso2/wso2ei-business-process:6.5.0
//
//  without this instrumentation, the following error will appear in the docker logs:
//    java.lang.ClassNotFoundException: org.wso2.carbon.humantask.ui.fileupload.HumanTaskUploadExecutor
//                                      cannot be found by org.wso2.carbon.ui_4.4.36
//
//  ... or even better, write a standalone OSGi application that exhibits similar issue,
//    so we can run against arbitrary (e.g. latest) Eclipse OSGi release, especially since
//    this instrumentation patches a private method which could be renamed at any time
