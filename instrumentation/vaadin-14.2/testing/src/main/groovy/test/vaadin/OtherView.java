/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.vaadin;

import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("other")
public class OtherView extends VerticalLayout {

  public OtherView() {
    Label label = new Label("Other view");
    label.setId("other.label");
    add(label);
  }
}
