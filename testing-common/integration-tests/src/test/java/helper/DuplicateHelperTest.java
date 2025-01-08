package helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DuplicateHelperTest {

  @Test
  void duplicateHelper() {
    String string = DuplicateHelperTestClass.transform("test");
    assertThat(string).isEqualTo("test foo");
  }
}
