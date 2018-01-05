package datadog.opentracing.decorators;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class DecoratorsConfig {
  private List<DDSpanDecoratorConfig> decorators;

  public List<DDSpanDecoratorConfig> getDecorators() {
    return decorators;
  }

  public void setDecorators(final List<DDSpanDecoratorConfig> decorators) {
    this.decorators = decorators;
  }

  @Override
  public String toString() {
    try {
      return new ObjectMapper(new YAMLFactory()).writeValueAsString(this);
    } catch (final JsonProcessingException e) {
      // FIXME better toString() while config object stabilized
      return null;
    }
  }

  static class DDSpanDecoratorConfig {

    private String type;

    private String matchingTag;

    private String matchingValue;

    private String setTag;

    private String setValue;

    public String getMatchingTag() {
      return matchingTag;
    }

    public void setMatchingTag(final String matchingTag) {
      this.matchingTag = matchingTag;
    }

    public String getMatchingValue() {
      return matchingValue;
    }

    public void setMatchingValue(final String matchingValue) {
      this.matchingValue = matchingValue;
    }

    public String getSetTag() {
      return setTag;
    }

    public void setSetTag(final String setTag) {
      this.setTag = setTag;
    }

    public String getSetValue() {
      return setValue;
    }

    public void setSetValue(final String setValue) {
      this.setValue = setValue;
    }

    public void setType(final String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }

    @Override
    public String toString() {
      try {
        return new ObjectMapper(new YAMLFactory()).writeValueAsString(this);
      } catch (final JsonProcessingException e) {
        return null;
      }
    }
  }
}
