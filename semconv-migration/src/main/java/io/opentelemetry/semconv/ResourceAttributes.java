/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.semconv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.incubating.CloudIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import java.util.List;

public final class ResourceAttributes {
  private ResourceAttributes() {}

  public static final String SCHEMA_URL = SchemaUrls.V1_24_0;

  public static final AttributeKey<String> CLOUD_ACCOUNT_ID =
      CloudIncubatingAttributes.CLOUD_ACCOUNT_ID;
  public static final AttributeKey<String> CLOUD_AVAILABILITY_ZONE =
      CloudIncubatingAttributes.CLOUD_AVAILABILITY_ZONE;
  public static final AttributeKey<String> CLOUD_PLATFORM =
      CloudIncubatingAttributes.CLOUD_PLATFORM;
  public static final AttributeKey<String> CLOUD_PROVIDER =
      CloudIncubatingAttributes.CLOUD_PROVIDER;
  public static final AttributeKey<String> CLOUD_REGION = CloudIncubatingAttributes.CLOUD_REGION;
  public static final AttributeKey<String> CLOUD_RESOURCE_ID =
      CloudIncubatingAttributes.CLOUD_RESOURCE_ID;

  public static final class CloudProviderValues {
    public static final String ALIBABA_CLOUD =
        CloudIncubatingAttributes.CloudProviderValues.ALIBABA_CLOUD;
    public static final String AWS = CloudIncubatingAttributes.CloudProviderValues.AWS;
    public static final String AZURE = CloudIncubatingAttributes.CloudProviderValues.AZURE;
    public static final String GCP = CloudIncubatingAttributes.CloudProviderValues.GCP;
    public static final String HEROKU = CloudIncubatingAttributes.CloudProviderValues.HEROKU;
    public static final String IBM_CLOUD = CloudIncubatingAttributes.CloudProviderValues.IBM_CLOUD;
    public static final String TENCENT_CLOUD =
        CloudIncubatingAttributes.CloudProviderValues.TENCENT_CLOUD;

    private CloudProviderValues() {}
  }

  public static final class CloudPlatformValues {
    public static final String ALIBABA_CLOUD_ECS =
        CloudIncubatingAttributes.CloudPlatformValues.ALIBABA_CLOUD_ECS;
    public static final String ALIBABA_CLOUD_FC =
        CloudIncubatingAttributes.CloudPlatformValues.ALIBABA_CLOUD_FC;
    public static final String ALIBABA_CLOUD_OPENSHIFT =
        CloudIncubatingAttributes.CloudPlatformValues.ALIBABA_CLOUD_OPENSHIFT;
    public static final String AWS_EC2 = CloudIncubatingAttributes.CloudPlatformValues.AWS_EC2;
    public static final String AWS_ECS = CloudIncubatingAttributes.CloudPlatformValues.AWS_ECS;
    public static final String AWS_EKS = CloudIncubatingAttributes.CloudPlatformValues.AWS_EKS;
    public static final String AWS_LAMBDA =
        CloudIncubatingAttributes.CloudPlatformValues.AWS_LAMBDA;
    public static final String AWS_ELASTIC_BEANSTALK =
        CloudIncubatingAttributes.CloudPlatformValues.AWS_ELASTIC_BEANSTALK;
    public static final String AWS_APP_RUNNER =
        CloudIncubatingAttributes.CloudPlatformValues.AWS_APP_RUNNER;
    public static final String AWS_OPENSHIFT =
        CloudIncubatingAttributes.CloudPlatformValues.AWS_OPENSHIFT;
    public static final String AZURE_VM = CloudIncubatingAttributes.CloudPlatformValues.AZURE_VM;
    public static final String AZURE_CONTAINER_INSTANCES =
        CloudIncubatingAttributes.CloudPlatformValues.AZURE_CONTAINER_INSTANCES;
    public static final String AZURE_AKS = CloudIncubatingAttributes.CloudPlatformValues.AZURE_AKS;
    public static final String AZURE_FUNCTIONS =
        CloudIncubatingAttributes.CloudPlatformValues.AZURE_FUNCTIONS;
    public static final String AZURE_APP_SERVICE =
        CloudIncubatingAttributes.CloudPlatformValues.AZURE_APP_SERVICE;
    public static final String AZURE_OPENSHIFT =
        CloudIncubatingAttributes.CloudPlatformValues.AZURE_OPENSHIFT;
    public static final String GCP_BARE_METAL_SOLUTION =
        CloudIncubatingAttributes.CloudPlatformValues.GCP_BARE_METAL_SOLUTION;
    public static final String GCP_COMPUTE_ENGINE =
        CloudIncubatingAttributes.CloudPlatformValues.GCP_COMPUTE_ENGINE;
    public static final String GCP_CLOUD_RUN =
        CloudIncubatingAttributes.CloudPlatformValues.GCP_CLOUD_RUN;
    public static final String GCP_KUBERNETES_ENGINE =
        CloudIncubatingAttributes.CloudPlatformValues.GCP_KUBERNETES_ENGINE;
    public static final String GCP_CLOUD_FUNCTIONS =
        CloudIncubatingAttributes.CloudPlatformValues.GCP_CLOUD_FUNCTIONS;
    public static final String GCP_APP_ENGINE =
        CloudIncubatingAttributes.CloudPlatformValues.GCP_APP_ENGINE;
    public static final String GCP_OPENSHIFT =
        CloudIncubatingAttributes.CloudPlatformValues.GCP_OPENSHIFT;
    public static final String IBM_CLOUD_OPENSHIFT =
        CloudIncubatingAttributes.CloudPlatformValues.IBM_CLOUD_OPENSHIFT;
    public static final String TENCENT_CLOUD_CVM =
        CloudIncubatingAttributes.CloudPlatformValues.TENCENT_CLOUD_CVM;
    public static final String TENCENT_CLOUD_EKS =
        CloudIncubatingAttributes.CloudPlatformValues.TENCENT_CLOUD_EKS;
    public static final String TENCENT_CLOUD_SCF =
        CloudIncubatingAttributes.CloudPlatformValues.TENCENT_CLOUD_SCF;

    private CloudPlatformValues() {}
  }

  public static final AttributeKey<String> SERVICE_INSTANCE_ID =
      ServiceIncubatingAttributes.SERVICE_INSTANCE_ID;
  public static final AttributeKey<String> SERVICE_NAME = ServiceIncubatingAttributes.SERVICE_NAME;
  public static final AttributeKey<String> SERVICE_NAMESPACE =
      ServiceIncubatingAttributes.SERVICE_NAMESPACE;
  public static final AttributeKey<String> SERVICE_VERSION =
      ServiceIncubatingAttributes.SERVICE_VERSION;

  public static final AttributeKey<String> OS_BUILD_ID = OsIncubatingAttributes.OS_BUILD_ID;
  public static final AttributeKey<String> OS_DESCRIPTION = OsIncubatingAttributes.OS_DESCRIPTION;
  public static final AttributeKey<String> OS_NAME = OsIncubatingAttributes.OS_NAME;
  public static final AttributeKey<String> OS_TYPE = OsIncubatingAttributes.OS_TYPE;
  public static final AttributeKey<String> OS_VERSION = OsIncubatingAttributes.OS_VERSION;

  public static final class OsTypeValues {
    public static final String WINDOWS = OsIncubatingAttributes.OsTypeValues.WINDOWS;
    public static final String LINUX = OsIncubatingAttributes.OsTypeValues.LINUX;
    public static final String DARWIN = OsIncubatingAttributes.OsTypeValues.DARWIN;
    public static final String FREEBSD = OsIncubatingAttributes.OsTypeValues.FREEBSD;
    public static final String NETBSD = OsIncubatingAttributes.OsTypeValues.NETBSD;
    public static final String OPENBSD = OsIncubatingAttributes.OsTypeValues.OPENBSD;
    public static final String DRAGONFLYBSD = OsIncubatingAttributes.OsTypeValues.DRAGONFLYBSD;
    public static final String HPUX = OsIncubatingAttributes.OsTypeValues.HPUX;
    public static final String AIX = OsIncubatingAttributes.OsTypeValues.AIX;
    public static final String SOLARIS = OsIncubatingAttributes.OsTypeValues.SOLARIS;
    public static final String Z_OS = OsIncubatingAttributes.OsTypeValues.Z_OS;

    private OsTypeValues() {}
  }

  public static final AttributeKey<String> TELEMETRY_DISTRO_NAME =
      TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME;
  public static final AttributeKey<String> TELEMETRY_DISTRO_VERSION =
      TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION;
  public static final AttributeKey<String> TELEMETRY_SDK_LANGUAGE =
      TelemetryIncubatingAttributes.TELEMETRY_SDK_LANGUAGE;
  public static final AttributeKey<String> TELEMETRY_SDK_NAME =
      TelemetryIncubatingAttributes.TELEMETRY_SDK_NAME;
  public static final AttributeKey<String> TELEMETRY_SDK_VERSION =
      TelemetryIncubatingAttributes.TELEMETRY_SDK_VERSION;

  public static final class TelemetrySdkLanguageValues {
    public static final String CPP = TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.CPP;
    public static final String DOTNET =
        TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.DOTNET;
    public static final String ERLANG =
        TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.ERLANG;
    public static final String GO = TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.GO;
    public static final String JAVA = TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.JAVA;
    public static final String NODEJS =
        TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.NODEJS;
    public static final String PHP = TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.PHP;
    public static final String PYTHON =
        TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.PYTHON;
    public static final String RUBY = TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.RUBY;
    public static final String RUST = TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.RUST;
    public static final String SWIFT =
        TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.SWIFT;
    public static final String WEBJS =
        TelemetryIncubatingAttributes.TelemetrySdkLanguageValues.WEBJS;

    private TelemetrySdkLanguageValues() {}
  }

  public static final AttributeKey<String> CONTAINER_COMMAND =
      ContainerIncubatingAttributes.CONTAINER_COMMAND;
  public static final AttributeKey<List<String>> CONTAINER_COMMAND_ARGS =
      ContainerIncubatingAttributes.CONTAINER_COMMAND_ARGS;
  public static final AttributeKey<String> CONTAINER_COMMAND_LINE =
      ContainerIncubatingAttributes.CONTAINER_COMMAND_LINE;
  public static final AttributeKey<String> CONTAINER_ID =
      ContainerIncubatingAttributes.CONTAINER_ID;
  public static final AttributeKey<String> CONTAINER_IMAGE_ID =
      ContainerIncubatingAttributes.CONTAINER_IMAGE_ID;
  public static final AttributeKey<String> CONTAINER_IMAGE_NAME =
      ContainerIncubatingAttributes.CONTAINER_IMAGE_NAME;
  public static final AttributeKey<List<String>> CONTAINER_IMAGE_REPO_DIGESTS =
      ContainerIncubatingAttributes.CONTAINER_IMAGE_REPO_DIGESTS;
  public static final AttributeKey<List<String>> CONTAINER_IMAGE_TAGS =
      ContainerIncubatingAttributes.CONTAINER_IMAGE_TAGS;
  public static final AttributeKeyTemplate<String> CONTAINER_LABELS =
      ContainerIncubatingAttributes.CONTAINER_LABELS;
  public static final AttributeKey<String> CONTAINER_NAME =
      ContainerIncubatingAttributes.CONTAINER_NAME;
  public static final AttributeKey<String> CONTAINER_RUNTIME =
      ContainerIncubatingAttributes.CONTAINER_RUNTIME;

  public static final AttributeKey<String> PROCESS_COMMAND =
      ProcessIncubatingAttributes.PROCESS_COMMAND;
  public static final AttributeKey<List<String>> PROCESS_COMMAND_ARGS =
      ProcessIncubatingAttributes.PROCESS_COMMAND_ARGS;
  public static final AttributeKey<String> PROCESS_COMMAND_LINE =
      ProcessIncubatingAttributes.PROCESS_COMMAND_LINE;
  public static final AttributeKey<String> PROCESS_EXECUTABLE_NAME =
      ProcessIncubatingAttributes.PROCESS_EXECUTABLE_NAME;
  public static final AttributeKey<String> PROCESS_EXECUTABLE_PATH =
      ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH;
  public static final AttributeKey<String> PROCESS_OWNER =
      ProcessIncubatingAttributes.PROCESS_OWNER;
  public static final AttributeKey<Long> PROCESS_PARENT_PID =
      ProcessIncubatingAttributes.PROCESS_PARENT_PID;
  public static final AttributeKey<Long> PROCESS_PID = ProcessIncubatingAttributes.PROCESS_PID;
  public static final AttributeKey<String> PROCESS_RUNTIME_DESCRIPTION =
      ProcessIncubatingAttributes.PROCESS_RUNTIME_DESCRIPTION;
  public static final AttributeKey<String> PROCESS_RUNTIME_NAME =
      ProcessIncubatingAttributes.PROCESS_RUNTIME_NAME;
  public static final AttributeKey<String> PROCESS_RUNTIME_VERSION =
      ProcessIncubatingAttributes.PROCESS_RUNTIME_VERSION;

  public static final AttributeKey<String> HOST_ARCH = HostIncubatingAttributes.HOST_ARCH;
  public static final AttributeKey<Long> HOST_CPU_CACHE_L2_SIZE =
      HostIncubatingAttributes.HOST_CPU_CACHE_L2_SIZE;
  public static final AttributeKey<String> HOST_CPU_FAMILY =
      HostIncubatingAttributes.HOST_CPU_FAMILY;
  public static final AttributeKey<String> HOST_CPU_MODEL_ID =
      HostIncubatingAttributes.HOST_CPU_MODEL_ID;
  public static final AttributeKey<String> HOST_CPU_MODEL_NAME =
      HostIncubatingAttributes.HOST_CPU_MODEL_NAME;
  public static final AttributeKey<Long> HOST_CPU_STEPPING =
      HostIncubatingAttributes.HOST_CPU_STEPPING;
  public static final AttributeKey<String> HOST_CPU_VENDOR_ID =
      HostIncubatingAttributes.HOST_CPU_VENDOR_ID;
  public static final AttributeKey<String> HOST_ID = HostIncubatingAttributes.HOST_ID;
  public static final AttributeKey<String> HOST_IMAGE_ID = HostIncubatingAttributes.HOST_IMAGE_ID;
  public static final AttributeKey<String> HOST_IMAGE_NAME =
      HostIncubatingAttributes.HOST_IMAGE_NAME;
  public static final AttributeKey<String> HOST_IMAGE_VERSION =
      HostIncubatingAttributes.HOST_IMAGE_VERSION;
  public static final AttributeKey<List<String>> HOST_IP = HostIncubatingAttributes.HOST_IP;
  public static final AttributeKey<List<String>> HOST_MAC = HostIncubatingAttributes.HOST_MAC;
  public static final AttributeKey<String> HOST_NAME = HostIncubatingAttributes.HOST_NAME;
  public static final AttributeKey<String> HOST_TYPE = HostIncubatingAttributes.HOST_TYPE;

  public static final class HostArchValues {
    public static final String AMD64 = HostIncubatingAttributes.HostArchValues.AMD64;
    public static final String ARM32 = HostIncubatingAttributes.HostArchValues.ARM32;
    public static final String ARM64 = HostIncubatingAttributes.HostArchValues.ARM64;
    public static final String IA64 = HostIncubatingAttributes.HostArchValues.IA64;
    public static final String PPC32 = HostIncubatingAttributes.HostArchValues.PPC32;
    public static final String PPC64 = HostIncubatingAttributes.HostArchValues.PPC64;
    public static final String S390X = HostIncubatingAttributes.HostArchValues.S390X;
    public static final String X86 = HostIncubatingAttributes.HostArchValues.X86;

    private HostArchValues() {}
  }

  public static final AttributeKey<Boolean> FAAS_COLDSTART =
      FaasIncubatingAttributes.FAAS_COLDSTART;
  public static final AttributeKey<String> FAAS_CRON = FaasIncubatingAttributes.FAAS_CRON;
  public static final AttributeKey<String> FAAS_DOCUMENT_COLLECTION =
      FaasIncubatingAttributes.FAAS_DOCUMENT_COLLECTION;
  public static final AttributeKey<String> FAAS_DOCUMENT_NAME =
      FaasIncubatingAttributes.FAAS_DOCUMENT_NAME;
  public static final AttributeKey<String> FAAS_DOCUMENT_OPERATION =
      FaasIncubatingAttributes.FAAS_DOCUMENT_OPERATION;
  public static final AttributeKey<String> FAAS_DOCUMENT_TIME =
      FaasIncubatingAttributes.FAAS_DOCUMENT_TIME;
  public static final AttributeKey<String> FAAS_INSTANCE = FaasIncubatingAttributes.FAAS_INSTANCE;
  public static final AttributeKey<String> FAAS_INVOCATION_ID =
      FaasIncubatingAttributes.FAAS_INVOCATION_ID;
  public static final AttributeKey<String> FAAS_INVOKED_NAME =
      FaasIncubatingAttributes.FAAS_INVOKED_NAME;
  public static final AttributeKey<String> FAAS_INVOKED_PROVIDER =
      FaasIncubatingAttributes.FAAS_INVOKED_PROVIDER;
  public static final AttributeKey<String> FAAS_INVOKED_REGION =
      FaasIncubatingAttributes.FAAS_INVOKED_REGION;
  public static final AttributeKey<Long> FAAS_MAX_MEMORY = FaasIncubatingAttributes.FAAS_MAX_MEMORY;
  public static final AttributeKey<String> FAAS_NAME = FaasIncubatingAttributes.FAAS_NAME;
  public static final AttributeKey<String> FAAS_TIME = FaasIncubatingAttributes.FAAS_TIME;
  public static final AttributeKey<String> FAAS_TRIGGER = FaasIncubatingAttributes.FAAS_TRIGGER;
  public static final AttributeKey<String> FAAS_VERSION = FaasIncubatingAttributes.FAAS_VERSION;

  public static final class FaasTriggerValues {
    public static final String DATASOURCE = FaasIncubatingAttributes.FaasTriggerValues.DATASOURCE;
    public static final String HTTP = FaasIncubatingAttributes.FaasTriggerValues.HTTP;
    public static final String PUBSUB = FaasIncubatingAttributes.FaasTriggerValues.PUBSUB;
    public static final String TIMER = FaasIncubatingAttributes.FaasTriggerValues.TIMER;
    public static final String OTHER = FaasIncubatingAttributes.FaasTriggerValues.OTHER;

    private FaasTriggerValues() {}
  }

  public static final class FaasInvokedProviderValues {
    public static final String ALIBABA_CLOUD =
        FaasIncubatingAttributes.FaasInvokedProviderValues.ALIBABA_CLOUD;
    public static final String AWS = FaasIncubatingAttributes.FaasInvokedProviderValues.AWS;
    public static final String AZURE = FaasIncubatingAttributes.FaasInvokedProviderValues.AZURE;
    public static final String GCP = FaasIncubatingAttributes.FaasInvokedProviderValues.GCP;
    public static final String TENCENT_CLOUD =
        FaasIncubatingAttributes.FaasInvokedProviderValues.TENCENT_CLOUD;

    private FaasInvokedProviderValues() {}
  }

  public static final class FaasDocumentOperationValues {
    public static final String INSERT = FaasIncubatingAttributes.FaasDocumentOperationValues.INSERT;
    public static final String EDIT = FaasIncubatingAttributes.FaasDocumentOperationValues.EDIT;
    public static final String DELETE = FaasIncubatingAttributes.FaasDocumentOperationValues.DELETE;

    private FaasDocumentOperationValues() {}
  }
}
