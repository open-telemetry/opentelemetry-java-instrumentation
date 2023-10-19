/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.vaadin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import java.lang.reflect.Method;

@Route("main")
public class MainView extends VerticalLayout {

  private static final long serialVersionUID = 1L;

  public MainView() {
    Label label = new Label("Main view");
    label.setId("main.label");
    Button button = new Button("To other view", e -> navigate(OtherView.class));
    button.setId("main.button");
    add(label, button);
  }

  private static void navigate(Class<?> navigationTarget) {
    try {
      // using reflection because return type of the method changes from void to Optional
      Method method = UI.class.getMethod("navigate", Class.class);
      method.invoke(UI.getCurrent(), navigationTarget);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
