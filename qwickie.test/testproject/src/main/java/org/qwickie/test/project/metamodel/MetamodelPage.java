package org.qwickie.test.project.metamodel;

import static org.qwickie.test.project.metamodel.TestEntity_.TITLE;
import static org.qwickie.test.project.metamodel.TestEntity_.DESCRIPTION;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

public class MetamodelPage extends WebPage {

	private static final long serialVersionUID = 1L;

	public MetamodelPage() {
		add(new Label(TITLE));
		add(new Label(DESCRIPTION));
	}
}
