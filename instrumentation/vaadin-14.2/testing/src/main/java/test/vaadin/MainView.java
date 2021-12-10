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

@Route("main")
public class MainView extends VerticalLayout {

  public MainView() {
    Label label = new Label("Main view");
    label.setId("main.label");
    Button button = new Button("To other view", e -> UI.getCurrent().navigate(OtherView.class));
    button.setId("main.button");
    add(label, button);
  }
}
