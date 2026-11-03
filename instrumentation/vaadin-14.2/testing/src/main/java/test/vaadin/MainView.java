/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.vaadin;

import static java.util.Objects.requireNonNull;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.Route;
import java.lang.reflect.Method;

@Route("main")
public class MainView extends VerticalLayout implements AfterNavigationObserver {

  private static final long serialVersionUID = 1L;
  private static volatile int uiId = -1;
  private static volatile int buttonNodeId = -1;
  private static volatile String csrfToken;
  private final Button button;

  public MainView() {
    Label label = new Label("Main view");
    label.setId("main.label");
    button = new Button("To other view", e -> navigate(OtherView.class));
    button.setId("main.button");
    add(label, button);
  }

  @Override
  public void afterNavigation(AfterNavigationEvent event) {
    UI ui = requireNonNull(UI.getCurrent());
    uiId = ui.getUIId();
    buttonNodeId = button.getElement().getNode().getId();
    csrfToken = ui.getCsrfToken();
    if (csrfToken == null) {
      try {
        csrfToken =
            (String) ui.getSession().getClass().getMethod("getCsrfToken").invoke(ui.getSession());
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public static int getUiId() {
    return uiId;
  }

  public static int getButtonNodeId() {
    return buttonNodeId;
  }

  public static String getCsrfToken() {
    return csrfToken;
  }

  private static void navigate(Class<?> navigationTarget) {
    try {
      // using reflection because return type of the method changes from void to Optional
      Method method = UI.class.getMethod("navigate", Class.class);
      method.invoke(UI.getCurrent(), navigationTarget);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
