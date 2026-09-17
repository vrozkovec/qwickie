package qwickie.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.junit.Before;
import org.junit.Test;

import qwickie.util.ConstantResolver.ResolvedConstant;

public class ConstantResolverTest {
	private final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("testproject");

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testFindConstantUsageLine() {
		long start = System.nanoTime();

		final String source = "package test;\n"
				+ "import static com.example.Entity_.TITLE;\n"
				+ "// TITLE is used here in a comment\n"
				+ "public class MyPage {\n"
				+ "    public MyPage() {\n"
				+ "        add(new Label(TITLE));\n"
				+ "    }\n"
				+ "}\n";

		// should find TITLE on usage line (line 5, 0-based), skipping import and comment
		assertEquals(5, ConstantResolver.findConstantUsageLine(source, "TITLE"));

		// should not find a non-existent constant
		assertEquals(-1, ConstantResolver.findConstantUsageLine(source, "NONEXISTENT"));

		// null source should return -1
		assertEquals(-1, ConstantResolver.findConstantUsageLine(null, "TITLE"));

		System.out.println("testFindConstantUsageLine:\t" + (System.nanoTime() - start));
	}

	@Test
	public void testFindConstantUsageLineBlockComment() {
		long start = System.nanoTime();

		final String source = "package test;\n"
				+ "/*\n"
				+ " * TITLE is mentioned in block comment\n"
				+ " */\n"
				+ "public class MyPage {\n"
				+ "    add(new Label(TITLE));\n"
				+ "}\n";

		// should skip block comment and find on line 5
		assertEquals(5, ConstantResolver.findConstantUsageLine(source, "TITLE"));

		System.out.println("testFindConstantUsageLineBlockComment:\t" + (System.nanoTime() - start));
	}

	@Test
	public void testContainsWholeWord() {
		long start = System.nanoTime();

		assertTrue(ConstantResolver.containsWholeWord("add(new Label(TITLE))", "TITLE"));
		assertTrue(ConstantResolver.containsWholeWord("TITLE", "TITLE"));
		assertTrue(ConstantResolver.containsWholeWord("  TITLE  ", "TITLE"));

		// SUBTITLE contains TITLE but it's not a whole word
		assertFalse(ConstantResolver.containsWholeWord("add(new Label(SUBTITLE))", "TITLE"));
		assertFalse(ConstantResolver.containsWholeWord("TITLE_LONG", "TITLE"));
		assertFalse(ConstantResolver.containsWholeWord("MY_TITLE", "TITLE"));

		// word boundaries with non-identifier chars
		assertTrue(ConstantResolver.containsWholeWord("(TITLE)", "TITLE"));
		assertTrue(ConstantResolver.containsWholeWord("x=TITLE;", "TITLE"));

		System.out.println("testContainsWholeWord:\t\t" + (System.nanoTime() - start));
	}

	@Test
	public void testResolveStaticImportConstants() {
		long start = System.nanoTime();

		final IFile javaFile = project.getFile("src/main/java/org/qwickie/test/project/metamodel/MetamodelPage.java");
		assertTrue("MetamodelPage.java should exist", javaFile.exists());
		final ICompilationUnit icu = JavaCore.createCompilationUnitFrom(javaFile);
		assertNotNull(icu);

		final List<ResolvedConstant> constants = ConstantResolver.resolveStaticImportConstants(icu);
		assertNotNull(constants);
		assertTrue("Should resolve at least 2 constants", constants.size() >= 2);

		// verify TITLE constant
		boolean foundTitle = false;
		boolean foundDescription = false;
		for (final ResolvedConstant rc : constants) {
			if ("TITLE".equals(rc.getConstantName())) {
				assertEquals("title", rc.getValue());
				foundTitle = true;
			}
			if ("DESCRIPTION".equals(rc.getConstantName())) {
				assertEquals("description", rc.getValue());
				foundDescription = true;
			}
		}
		assertTrue("TITLE constant should be resolved", foundTitle);
		assertTrue("DESCRIPTION constant should be resolved", foundDescription);

		System.out.println("testResolveStaticImportConstants:\t" + (System.nanoTime() - start));
	}

	@Test
	public void testFindConstantNameForValue() {
		long start = System.nanoTime();

		final IFile javaFile = project.getFile("src/main/java/org/qwickie/test/project/metamodel/MetamodelPage.java");
		final ICompilationUnit icu = JavaCore.createCompilationUnitFrom(javaFile);

		assertEquals("TITLE", ConstantResolver.findConstantNameForValue(icu, "title"));
		assertEquals("DESCRIPTION", ConstantResolver.findConstantNameForValue(icu, "description"));
		assertNull(ConstantResolver.findConstantNameForValue(icu, "nonexistent"));

		System.out.println("testFindConstantNameForValue:\t" + (System.nanoTime() - start));
	}
}
