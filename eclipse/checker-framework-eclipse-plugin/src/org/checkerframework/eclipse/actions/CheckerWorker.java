package org.checkerframework.eclipse.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.checkerframework.eclipse.CheckerPlugin;
import org.checkerframework.eclipse.javac.CheckersRunner;
import org.checkerframework.eclipse.javac.CommandlineJavacRunner;
import org.checkerframework.eclipse.javac.JavacError;
import org.checkerframework.eclipse.javac.JavacRunner;
import org.checkerframework.eclipse.util.MarkerUtil;
import org.checkerframework.eclipse.util.Paths;
import org.checkerframework.eclipse.util.PluginUtil;
import org.checkerframework.eclipse.util.ResourceUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.sun.tools.javac.util.Pair;

//TODO: RENAME THIS TO CHECKER JOB
public class CheckerWorker extends Job {
	private final String checkerNames;

	private Set<IJavaElement> javaElements;
	private Map<String, IJavaElement> sourceFiles;

	private final String javacJreVersion = "1.8.0";

	private final boolean useJavacRunner;
	private final boolean hasQuals;

	public CheckerWorker(Set<IJavaElement> elements, String checkerNames,
			boolean hasQuals) {
		super("Running checker on "
				+ ((elements.size() < 10) ? PluginUtil.join(",", elements)
						: elements.size() + " items"));
		this.checkerNames = checkerNames;
		this.useJavacRunner = shouldUseJavacRunner();

		this.javaElements = new HashSet<IJavaElement>();
		this.sourceFiles = new HashMap<String, IJavaElement>();

		this.hasQuals = hasQuals;
		try {
			for (IJavaElement givenElement : elements) {
				this.javaElements.addAll(ResourceUtils
						.javaElementsOf(givenElement));
			}

			for (IJavaElement javaElement : javaElements) {
				this.sourceFiles.put(javaElement.getResource().getLocation()
						.toOSString(), javaElement);
			}

		} catch (CoreException e) {
			CheckerPlugin.logException(e, e.getMessage());
		}
	}

	private boolean shouldUseJavacRunner() { /*
											 * int expectedLength =
											 * "1.x.x".length(); final String
											 * jreVersion =
											 * System.getProperties(
											 * ).getProperty
											 * ("java.runtime.version"
											 * ).substring(0, expectedLength);
											 * return
											 * jreVersion.equals(javacJreVersion
											 * );
											 */
		return false;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		try {
			work(monitor);
		} catch (Throwable e) {
			CheckerPlugin.logException(e, "Analysis exception");
			return Status.CANCEL_STATUS;
		}

		return Status.OK_STATUS;
	}

	private void work(final IProgressMonitor pm) throws CoreException {
		if (checkerNames != null) {
			pm.beginTask("Running checker(s) " + checkerNames.toString()
					+ " on " + sourceFiles.toString(), 10);
		} else {
			pm.beginTask("Running custom single checker " + " on "
					+ sourceFiles.toString(), 10);
		}

		pm.setTaskName("Removing old markers");
		for (IJavaElement javaElement : javaElements) {
			MarkerUtil.removeMarkers(javaElement.getResource());
		}
		pm.worked(1);

		pm.setTaskName("Running checker");
		List<JavacError> callJavac = runChecker();
		pm.worked(6);

		pm.setTaskName("Updating problem list");
		markErrors(callJavac);
		pm.worked(3);

		pm.done();
	}

	private List<JavacError> runChecker() throws JavaModelException {
		final Pair<String, String> classpaths = classPathOf(projectsFor(javaElements));

		final CheckersRunner runner;
		if (useJavacRunner) {
			runner = new JavacRunner(sourceFiles.keySet().toArray(
					new String[] {}), checkerNames.split(","), classpaths.fst
					+ File.pathSeparator + classpaths.snd, hasQuals);
		} else {
			runner = new CommandlineJavacRunner(sourceFiles.keySet().toArray(
					new String[] {}), checkerNames.split(","), classpaths.fst,
					classpaths.snd, hasQuals);
		}
		runner.run();

		return runner.getErrors();
	}

	private void markErrors(List<JavacError> errors) {
		for (JavacError error : errors) {
			if (error.file == null) {
				continue;
			}

			IJavaElement javaElement = sourceFiles.get(error.file.toString());
			if (javaElement == null)
				continue;
			MarkerUtil.addMarker(error.message, javaElement.getJavaProject()
					.getProject(), javaElement.getResource(), error.lineNumber,
					error.errorKey, error.errorArguments, error.startPosition,
					error.endPosition);
		}
	}

	private Pair<List<String>, List<String>> pathOf(IClasspathEntry cp,
			IJavaProject project) throws JavaModelException {
		int entryKind = cp.getEntryKind();
		switch (entryKind) {
		case IClasspathEntry.CPE_SOURCE:
			return new Pair<List<String>, List<String>>(
					Arrays.asList(new String[] { ResourceUtils.outputLocation(
							cp, project) }), new ArrayList<String>());

		case IClasspathEntry.CPE_LIBRARY:
			return new Pair<List<String>, List<String>>(
					Arrays.asList(new String[] { Paths.absolutePathOf(cp) }),
					new ArrayList<String>());

		case IClasspathEntry.CPE_PROJECT:
			return projectPathOf(cp);

		case IClasspathEntry.CPE_CONTAINER:
			List<String> resultPaths = new ArrayList<String>();
			List<String> resultBootPaths = new ArrayList<String>();
			IClasspathContainer c = JavaCore.getClasspathContainer(
					cp.getPath(), project);
			if (c.getKind() == IClasspathContainer.K_DEFAULT_SYSTEM
					|| c.getKind() == IClasspathContainer.K_SYSTEM) {
				for (IClasspathEntry entry : c.getClasspathEntries()) {
					if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
						resultBootPaths.add(entry.getPath().makeAbsolute()
								.toFile().getAbsolutePath());
					}
				}
			} else {
				for (IClasspathEntry entry : c.getClasspathEntries()) {
					if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
						resultPaths.add(entry.getPath().makeAbsolute().toFile()
								.getAbsolutePath());
					}
				}
			}
			return new Pair<List<String>, List<String>>(resultPaths,
					resultBootPaths);

		case IClasspathEntry.CPE_VARIABLE:
			return pathOf(JavaCore.getResolvedClasspathEntry(cp), project);

		}

		return new Pair<List<String>, List<String>>(new ArrayList<String>(),
				new ArrayList<String>());
	}

	/**
	 * Returns the project's classpath in a format suitable for javac
	 *
	 * @param project
	 * @return the project's classpath as a string
	 * @throws JavaModelException
	 */
	private Pair<String, String> classPathOf(Collection<IJavaProject> projects)
			throws JavaModelException {
		Pair<List<String>, List<String>> paths = classPathEntries(projects);

		return new Pair<String, String>(PluginUtil.join(File.pathSeparator,
				paths.fst), PluginUtil.join(File.pathSeparator, paths.snd));
	}

	private Pair<List<String>, List<String>> classPathEntries(
			Collection<IJavaProject> projects) throws JavaModelException {

		LinkedHashSet<String> fst = new LinkedHashSet<String>();
		LinkedHashSet<String> snd = new LinkedHashSet<String>();

		for (IJavaProject project : projects) {
			for (IClasspathEntry cp : project.getResolvedClasspath(true)) {
				Pair<List<String>, List<String>> paths = pathOf(cp, project);
				fst.addAll(paths.fst);
				snd.addAll(paths.snd);
			}
		}

		return new Pair<List<String>, List<String>>(new ArrayList<String>(fst),
				new ArrayList<String>(snd));
	}

	private Pair<List<String>, List<String>> projectPathOf(IClasspathEntry entry)
			throws JavaModelException {
		final IProject project = ResourceUtils.workspaceRoot().getProject(
				entry.getPath().toOSString());
		return classPathEntries(Collections.singleton(JavaCore.create(project)));
	}

	private Set<IJavaProject> projectsFor(Collection<IJavaElement> javaElements) {
		Set<IJavaProject> projects = new HashSet<IJavaProject>();
		for (IJavaElement javaElement : javaElements) {
			projects.add(javaElement.getJavaProject());
		}
		return projects;
	}
}
