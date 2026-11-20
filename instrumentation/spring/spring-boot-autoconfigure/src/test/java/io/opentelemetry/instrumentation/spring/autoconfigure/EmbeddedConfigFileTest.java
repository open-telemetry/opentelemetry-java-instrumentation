/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.AttributeNameValueModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.TextMapPropagatorModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.ViewModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalInstrumentationModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class EmbeddedConfigFileTest {

  @Test
  void convertFlatPropsToNested_simpleProperties() {
    Map<String, String> flatProps = new HashMap<>();
    flatProps.put("resource.service.name", "my-service");
    flatProps.put("traces.exporter", "otlp");

    Map<String, Object> result = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);

    assertThat(result)
        .containsOnlyKeys("resource", "traces")
        .satisfies(
            map -> {
              assertThat(map.get("resource"))
                  .isInstanceOf(Map.class)
                  .satisfies(
                      resource -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resourceMap = (Map<String, Object>) resource;
                        assertThat(resourceMap)
                            .containsOnlyKeys("service")
                            .satisfies(
                                m -> {
                                  @SuppressWarnings("unchecked")
                                  Map<String, Object> serviceMap =
                                      (Map<String, Object>) m.get("service");
                                  assertThat(serviceMap).containsEntry("name", "my-service");
                                });
                      });
              assertThat(map.get("traces"))
                  .isInstanceOf(Map.class)
                  .satisfies(
                      traces -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> tracesMap = (Map<String, Object>) traces;
                        assertThat(tracesMap).containsEntry("exporter", "otlp");
                      });
            });
  }

  @Test
  void convertFlatPropsToNested_arrayProperties() {
    Map<String, String> flatProps = new HashMap<>();
    flatProps.put("instrumentation.java.list[0]", "one");
    flatProps.put("instrumentation.java.list[1]", "two");
    flatProps.put("instrumentation.java.list[2]", "three");

    Map<String, Object> result = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);

    assertThat(result)
        .containsOnlyKeys("instrumentation")
        .satisfies(
            map -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> instrumentation =
                  (Map<String, Object>) map.get("instrumentation");
              assertThat(instrumentation)
                  .containsOnlyKeys("java")
                  .satisfies(
                      m -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> javaMap = (Map<String, Object>) m.get("java");
                        assertThat(javaMap)
                            .containsOnlyKeys("list")
                            .satisfies(
                                java -> {
                                  @SuppressWarnings("unchecked")
                                  List<Object> list = (List<Object>) java.get("list");
                                  assertThat(list).containsExactly("one", "two", "three");
                                });
                      });
            });
  }

  @Test
  void convertFlatPropsToNested_mixedPropertiesAndArrays() {
    Map<String, String> flatProps = new HashMap<>();
    flatProps.put("resource.service.name", "test-service");
    flatProps.put("resource.attributes[0]", "key1=value1");
    flatProps.put("resource.attributes[1]", "key2=value2");
    flatProps.put("traces.exporter", "otlp");

    Map<String, Object> result = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);

    assertThat(result)
        .containsOnlyKeys("resource", "traces")
        .satisfies(
            map -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> resource = (Map<String, Object>) map.get("resource");
              assertThat(resource)
                  .containsKeys("service", "attributes")
                  .satisfies(
                      r -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> service = (Map<String, Object>) r.get("service");
                        assertThat(service).containsEntry("name", "test-service");

                        @SuppressWarnings("unchecked")
                        List<Object> attributes = (List<Object>) r.get("attributes");
                        assertThat(attributes).containsExactly("key1=value1", "key2=value2");
                      });

              @SuppressWarnings("unchecked")
              Map<String, Object> traces = (Map<String, Object>) map.get("traces");
              assertThat(traces).containsEntry("exporter", "otlp");
            });
  }

  @Test
  void convertFlatPropsToNested_emptyMap() {
    Map<String, String> flatProps = new HashMap<>();

    Map<String, Object> result = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);

    assertThat(result).isEmpty();
  }

  @Test
  void convertFlatPropsToNested_singleLevelProperty() {
    Map<String, String> flatProps = new HashMap<>();
    flatProps.put("enabled", "true");

    Map<String, Object> result = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);

    assertThat(result).containsEntry("enabled", "true");
  }

  @Test
  void convertFlatPropsToNested_arrayWithGaps() {
    Map<String, String> flatProps = new HashMap<>();
    flatProps.put("list[0]", "first");
    flatProps.put("list[2]", "third");

    Map<String, Object> result = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);

    assertThat(result)
        .containsOnlyKeys("list")
        .satisfies(
            map -> {
              @SuppressWarnings("unchecked")
              List<Object> list = (List<Object>) map.get("list");
              assertThat(list).hasSize(3).containsExactly("first", null, "third");
            });
  }

  @Test
  void convertFlatPropsToNested_deeplyNestedProperties() {
    Map<String, String> flatProps = new HashMap<>();
    flatProps.put("a.b.c.d.e", "deep-value");

    Map<String, Object> result = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);

    assertThat(result).containsOnlyKeys("a");
    @SuppressWarnings("unchecked")
    Map<String, Object> a = (Map<String, Object>) result.get("a");
    assertThat(a).containsOnlyKeys("b");
    @SuppressWarnings("unchecked")
    Map<String, Object> b = (Map<String, Object>) a.get("b");
    assertThat(b).containsOnlyKeys("c");
    @SuppressWarnings("unchecked")
    Map<String, Object> c = (Map<String, Object>) b.get("c");
    assertThat(c).containsOnlyKeys("d");
    @SuppressWarnings("unchecked")
    Map<String, Object> d = (Map<String, Object>) c.get("d");
    assertThat(d).containsEntry("e", "deep-value");
  }

  @Test
  void convertFlatPropsToNested_nestedArrays() {
    Map<String, String> flatProps = new HashMap<>();
    flatProps.put("outer[0].inner[0]", "value1");
    flatProps.put("outer[0].inner[1]", "value2");
    flatProps.put("outer[1].inner[0]", "value3");

    Map<String, Object> result = EmbeddedConfigFile.convertFlatPropsToNested(flatProps);

    assertThat(result)
        .containsOnlyKeys("outer")
        .satisfies(
            map -> {
              @SuppressWarnings("unchecked")
              List<Object> outer = (List<Object>) map.get("outer");
              assertThat(outer).hasSize(2);

              @SuppressWarnings("unchecked")
              Map<String, Object> firstElement = (Map<String, Object>) outer.get(0);
              @SuppressWarnings("unchecked")
              List<Object> firstInner = (List<Object>) firstElement.get("inner");
              assertThat(firstInner).containsExactly("value1", "value2");

              @SuppressWarnings("unchecked")
              Map<String, Object> secondElement = (Map<String, Object>) outer.get(1);
              @SuppressWarnings("unchecked")
              List<Object> secondInner = (List<Object>) secondElement.get("inner");
              assertThat(secondInner).containsExactly("value3");
            });
  }

  @Test
  void extractModel_listFromHigherPrioritySourceReplacesWholeList() {
    Map<String, Object> base = new HashMap<>();
    base.put("otel.file_format", "1.1");
    base.put("otel.resource.schema_url", "https://base");
    base.put("otel.resource.attributes[0].name", "a");
    base.put("otel.resource.attributes[0].value", "1");
    base.put("otel.resource.attributes[1].name", "b");
    base.put("otel.resource.attributes[1].value", "2");
    base.put("otel.propagator.composite[0].tracecontext", "");
    base.put("otel.propagator.composite[1].baggage", "");
    Map<String, Object> profile = new HashMap<>();
    profile.put("otel.resource.attributes[0].name", "c");
    profile.put("otel.resource.attributes[0].value", "3");
    profile.put("otel.propagator.composite[0].b3", "");

    OpenTelemetryConfigurationModel model = extractModel(profile, base);

    // like Spring Boot, the list from the highest-priority source is used as a whole
    assertThat(model.getResource().getAttributes())
        .extracting(AttributeNameValueModel::getName)
        .containsExactly("c");
    List<TextMapPropagatorModel> composite = model.getPropagator().getComposite();
    assertThat(composite).hasSize(1);
    assertThat(composite.get(0).getB3()).isNotNull();
    assertThat(composite.get(0).getTracecontext()).isNull();
    // scalars are still overridden key by key
    assertThat(model.getResource().getSchemaUrl()).isEqualTo("https://base");
  }

  @Test
  void extractModel_listOnlyInLowerPrioritySourceIsKept() {
    Map<String, Object> base = new HashMap<>();
    base.put("otel.file_format", "1.1");
    base.put("otel.resource.attributes[0].name", "a");
    base.put("otel.resource.attributes[0].value", "1");
    Map<String, Object> profile = new HashMap<>();
    profile.put("otel.resource.schema_url", "https://profile");

    OpenTelemetryConfigurationModel model = extractModel(profile, base);

    assertThat(model.getResource().getAttributes())
        .extracting(AttributeNameValueModel::getName)
        .containsExactly("a");
    assertThat(model.getResource().getSchemaUrl()).isEqualTo("https://profile");
  }

  @Test
  void extractModel_bracketedMapKeysAreMergedAcrossSources() {
    Map<String, Object> base = new HashMap<>();
    base.put("otel.file_format", "1.1");
    base.put("otel.instrumentation/development.java.foo.[/low]", "L");
    Map<String, Object> profile = new HashMap<>();
    profile.put("otel.instrumentation/development.java.foo.[/high]", "H");
    profile.put("otel.instrumentation/development.java.foo.[1]", "H1");
    base.put("otel.instrumentation/development.java.foo.[0]", "L0");
    profile.put("otel.instrumentation/development.java.foo.[/route[0]]", "HR");
    base.put("otel.instrumentation/development.java.foo.[/route[1]]", "LR");

    OpenTelemetryConfigurationModel model = extractModel(profile, base);

    // brackets around a non-numeric key are Spring map-key notation, not a list index
    ExperimentalInstrumentationModel instrumentation =
        (ExperimentalInstrumentationModel)
            model.getExtensionProperties().get("instrumentation/development");
    assertThat(
            instrumentation
                .getJava()
                .getAdditionalProperties()
                .get("foo")
                .getAdditionalProperties())
        .hasSize(6);
  }

  @Test
  void extractModel_nullValueDoesNotClaimList() {
    Map<String, Object> base = new HashMap<>();
    base.put("otel.file_format", "1.1");
    base.put("otel.resource.attributes[0].name", "a");
    base.put("otel.resource.attributes[0].value", "1");
    base.put("otel.resource.attributes[1].name", "b");
    base.put("otel.resource.attributes[1].value", "2");
    Map<String, Object> profile = new HashMap<>();
    profile.put("otel.resource.attributes[0].name", null);

    OpenTelemetryConfigurationModel model = extractModel(profile, base);

    assertThat(model.getResource().getAttributes())
        .extracting(AttributeNameValueModel::getName, AttributeNameValueModel::getValue)
        .containsExactly(tuple("a", "1"), tuple("b", "2"));

    // a null field in the owning source is not filled in from a lower one
    profile.put("otel.resource.attributes[0].value", "3");

    model = extractModel(profile, base);

    assertThat(model.getResource().getAttributes())
        .extracting(AttributeNameValueModel::getName, AttributeNameValueModel::getValue)
        .containsExactly(tuple(null, "3"));
  }

  @Test
  void extractModel_nullListItemIsKept() {
    Map<String, Object> base = new HashMap<>();
    base.put("otel.file_format", "1.1");
    base.put("otel.propagator.composite[0].tracecontext", null);

    OpenTelemetryConfigurationModel model = extractModel(new HashMap<>(), base);

    assertThat(model.getPropagator().getComposite()).hasSize(1);
    assertThat(model.getPropagator().getComposite().get(0).getTracecontext()).isNotNull();
  }

  @Test
  void extractModel_nestedListBelongsToOuterListSource() {
    String view = "otel.meter_provider.views[0]";
    String boundaries = view + ".stream.aggregation.explicit_bucket_histogram.boundaries[0]";
    Map<String, Object> base = new HashMap<>();
    base.put("otel.file_format", "1.1");
    base.put(boundaries, "1.0");
    Map<String, Object> profile = new HashMap<>();
    profile.put(view + ".selector.instrument_name", "x");
    profile.put(boundaries, null);

    OpenTelemetryConfigurationModel model = extractModel(profile, base);

    // the source that owns the outer list also owns the lists nested in its items
    ViewModel viewModel = model.getMeterProvider().getViews().get(0);
    assertThat(viewModel.getSelector().getInstrumentName()).isEqualTo("x");
    assertThat(viewModel.getStream().getAggregation().getExplicitBucketHistogram().getBoundaries())
        .containsExactly((Double) null);
  }

  @Test
  void extractModel_nullScalarIsKept() {
    Map<String, Object> base = new HashMap<>();
    base.put("otel.file_format", "1.1");
    base.put("otel.resource", null);

    OpenTelemetryConfigurationModel model = extractModel(new HashMap<>(), base);

    // a present but null object property becomes an empty instance, as before
    assertThat(model.getResource()).isNotNull();
  }

  private static OpenTelemetryConfigurationModel extractModel(
      Map<String, Object> higherPriority, Map<String, Object> lowerPriority) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addLast(new MapPropertySource("higher", higherPriority));
    environment.getPropertySources().addLast(new MapPropertySource("lower", lowerPriority));
    return EmbeddedConfigFile.extractModel(environment);
  }
}
