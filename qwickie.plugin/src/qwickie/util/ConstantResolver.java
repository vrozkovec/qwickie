/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package qwickie.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Resolves JPA metamodel constants from static imports to their string values.
 * This enables the plugin to match wicket:id values in HTML with constant
 * references in Java code (e.g. {@code new Label(TITLE)} where {@code TITLE = "title"}).
 *
 * @author count.negative
 */
public final class ConstantResolver {

	private static final String LOG_FILE = "/data/tmp/qwickie.log";
	private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	/** A resolved constant mapping a constant name to its string value. */
	public static final class ResolvedConstant {
		private final String constantName;
		private final String value;

		public ResolvedConstant(final String constantName, final String value) {
			this.constantName = constantName;
			this.value = value;
		}

		public String getConstantName() {
			return constantName;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return constantName + "=\"" + value + "\"";
		}
	}

	private ConstantResolver() {
	}

	/**
	 * Logs a message to the qwickie log file.
	 *
	 * @param message the message to log
	 */
	public static void log(final String message) {
		try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
			pw.println(LocalDateTime.now().format(DTF) + " [ConstantResolver] " + message);
		} catch (final IOException e) {
			// ignore logging errors
		}
	}

	/**
	 * Resolves all static import constants from the given compilation unit.
	 * Examines static imports, resolves their types, and returns the constant
	 * name/value pairs for all {@code static final String} fields.
	 *
	 * @param icu the compilation unit to examine
	 * @return list of resolved constants, never null
	 */
	public static List<ResolvedConstant> resolveStaticImportConstants(final ICompilationUnit icu) {
		Assert.isNotNull(icu);
		final List<ResolvedConstant> results = new ArrayList<>();
		log("resolveStaticImportConstants() called for: " + icu.getElementName());
		try {
			final IJavaProject javaProject = icu.getJavaProject();
			final IImportDeclaration[] imports = icu.getImports();
			log("  total imports: " + imports.length);
			for (final IImportDeclaration imp : imports) {
				final boolean isStatic = Flags.isStatic(imp.getFlags());
				log("  import: " + imp.getElementName() + " static=" + isStatic + " onDemand=" + imp.isOnDemand());
				if (!isStatic) {
					continue;
				}
				final String importName = imp.getElementName();
				if (imp.isOnDemand()) {
					// import static com.example.Entity_.*
					final String typeName = importName.substring(0, importName.length() - 2); // remove .*
					log("  resolving on-demand type: " + typeName);
					final IType type = javaProject.findType(typeName);
					if (type != null) {
						log("  found type: " + type.getFullyQualifiedName() + " with " + type.getFields().length + " fields");
						addStringConstants(type.getFields(), results);
					} else {
						log("  type NOT found: " + typeName);
					}
				} else {
					// import static com.example.Entity_.TITLE
					final int lastDot = importName.lastIndexOf('.');
					if (lastDot == -1) {
						log("  skipping import with no dot");
						continue;
					}
					final String typeName = importName.substring(0, lastDot);
					final String fieldName = importName.substring(lastDot + 1);
					log("  resolving individual: type=" + typeName + " field=" + fieldName);
					final IType type = javaProject.findType(typeName);
					if (type != null) {
						final IField field = type.getField(fieldName);
						log("  field exists=" + (field != null && field.exists()));
						if (field != null && field.exists()) {
							addIfStringConstant(field, results);
						}
					} else {
						log("  type NOT found: " + typeName);
					}
				}
			}
		} catch (final JavaModelException e) {
			log("  JavaModelException: " + e.getMessage());
		}
		log("  resolved " + results.size() + " constants: " + results);
		return results;
	}

	/**
	 * Finds the constant name whose value matches the given wicket id.
	 *
	 * @param icu the compilation unit to examine
	 * @param wicketId the wicket:id value to search for
	 * @return the constant name (e.g. "TITLE") or null if not found
	 */
	public static String findConstantNameForValue(final ICompilationUnit icu, final String wicketId) {
		Assert.isNotNull(icu);
		Assert.isNotNull(wicketId);
		log("findConstantNameForValue() wicketId=\"" + wicketId + "\" in " + icu.getElementName());
		final List<ResolvedConstant> constants = resolveStaticImportConstants(icu);
		for (final ResolvedConstant rc : constants) {
			if (wicketId.equals(rc.getValue())) {
				log("  MATCH: " + rc.getConstantName() + " = \"" + rc.getValue() + "\"");
				return rc.getConstantName();
			}
		}
		log("  no match found for wicketId=\"" + wicketId + "\"");
		return null;
	}

	/**
	 * Finds the line number (0-based) where the constant name is used in source code,
	 * skipping import statements and comments.
	 *
	 * @param source the Java source code
	 * @param constantName the constant name to search for (e.g. "TITLE")
	 * @return the 0-based line number, or -1 if not found
	 */
	public static int findConstantUsageLine(final String source, final String constantName) {
		if (source == null || constantName == null) {
			return -1;
		}
		log("findConstantUsageLine() constantName=\"" + constantName + "\"");
		final String[] lines = source.split("\n");
		boolean inBlockComment = false;
		int lineNumber = 0;
		for (final String line : lines) {
			final String trimmed = line.trim();

			// skip import lines
			if (trimmed.startsWith("import ")) {
				lineNumber++;
				continue;
			}

			// track block comments
			if (trimmed.contains("/*")) {
				inBlockComment = true;
			}
			if (trimmed.contains("*/")) {
				inBlockComment = false;
				lineNumber++;
				continue;
			}

			// skip line comments and block comment contents
			if (trimmed.startsWith("//") || inBlockComment) {
				lineNumber++;
				continue;
			}

			if (containsWholeWord(line, constantName)) {
				log("  found at line " + lineNumber + ": " + trimmed);
				return lineNumber;
			}
			lineNumber++;
		}
		log("  not found in source");
		return -1;
	}

	/**
	 * Checks if the given text contains the word as a whole word,
	 * using Java identifier boundaries.
	 *
	 * @param text the text to search in
	 * @param word the word to search for
	 * @return true if the word is found as a whole word
	 */
	static boolean containsWholeWord(final String text, final String word) {
		int index = 0;
		while (index <= text.length() - word.length()) {
			index = text.indexOf(word, index);
			if (index == -1) {
				return false;
			}
			final boolean startOk = index == 0 || !Character.isJavaIdentifierPart(text.charAt(index - 1));
			final boolean endOk = (index + word.length()) == text.length()
					|| !Character.isJavaIdentifierPart(text.charAt(index + word.length()));
			if (startOk && endOk) {
				return true;
			}
			index++;
		}
		return false;
	}

	private static void addStringConstants(final IField[] fields, final List<ResolvedConstant> results) throws JavaModelException {
		for (final IField field : fields) {
			addIfStringConstant(field, results);
		}
	}

	private static void addIfStringConstant(final IField field, final List<ResolvedConstant> results) throws JavaModelException {
		final int flags = field.getFlags();
		if (!Flags.isStatic(flags) || !Flags.isFinal(flags)) {
			log("    field " + field.getElementName() + " skipped: static=" + Flags.isStatic(flags) + " final=" + Flags.isFinal(flags));
			return;
		}
		final String typeSignature = field.getTypeSignature();
		log("    field " + field.getElementName() + " typeSignature=" + typeSignature);
		// source types use "QString;" and binary types use "Ljava/lang/String;"
		if (!"QString;".equals(typeSignature) && !"Ljava/lang/String;".equals(typeSignature)) {
			log("    field " + field.getElementName() + " skipped: not a String type");
			return;
		}
		final Object constant = field.getConstant();
		log("    field " + field.getElementName() + " constant=" + constant + " (type=" + (constant != null ? constant.getClass().getSimpleName() : "null") + ")");
		if (constant instanceof String) {
			String value = (String) constant;
			// source types include surrounding quotes
			if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
				value = value.substring(1, value.length() - 1);
			}
			log("    RESOLVED: " + field.getElementName() + " = \"" + value + "\"");
			results.add(new ResolvedConstant(field.getElementName(), value));
		}
	}
}
