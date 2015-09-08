package org.checkerframework.eclipse.actions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.checkerframework.eclipse.CheckerPlugin;
import org.checkerframework.eclipse.prefs.CheckerPreferences;
import org.checkerframework.eclipse.util.MutexSchedulingRule;
import org.checkerframework.eclipse.util.PluginUtil;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 *
 * RunCheckerAction is an action handler that determines what
 *
 */
//TODO: Rename to RunCheckerHandler
//TODO: Remove all subclasses and just parameterize RunCheckerAction (perhaps take a list of checkers,
//TODO: or if no checkers are specified use custom checkers)
public abstract class RunCheckerAction extends CheckerHandler
{
    private final String checkerName;
    protected boolean usePrefs;
    protected boolean useCustom;
    protected boolean useSingleCustom;
    protected boolean hasQuals;

    /** true if this action is used from editor */
    protected boolean usedInEditor;

    protected RunCheckerAction() {
        super();
        this.checkerName = null;
        this.usePrefs = true;
        this.useCustom = false;
        this.useSingleCustom = false;
        this.hasQuals = true;
    }

    protected RunCheckerAction(String checkerName) {
        this(checkerName, true);
    }

    protected RunCheckerAction(String checkerName, boolean hasQuals) {
        super();
        this.checkerName = checkerName;
        this.useCustom = false;
        this.usePrefs = false;
        this.useSingleCustom = false;
        this.hasQuals = hasQuals;
    }

    /**
     * If constructed with a no-arg constructor, then we get the list of classes
     * to use from the preferences system
     */
    private List<String> getClassNameFromPrefs() {
        return CheckerManager.getSelectedClasses();
    }

    /**
     *
     */
    public Object execute(ExecutionEvent event)
    {
        // See if a file or project is selected in the explorer
        ISelection selection = getSelection(event);
        Set<IJavaElement> elements = new LinkedHashSet<IJavaElement>(selectionToJavaElements(selection));

        // Otherwise, try to get the file being actively edited
        if(elements.isEmpty()) {
        	IWorkbench workbench = PlatformUI.getWorkbench();
        	if(workbench != null) {
        		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        		if(window != null) {
        			IWorkbenchPage page = window.getActivePage();
        			if(page != null) {
        				IEditorPart editorPart = page.getActiveEditor();
        				if(editorPart != null) {
        					IEditorInput input = editorPart.getEditorInput();
        					if(input != null && input instanceof IFileEditorInput) {
        						IFile file = ((IFileEditorInput)input).getFile();
        						if(file != null) {
        							IJavaElement javaElement = JavaCore.create(file);
        							if(javaElement != null) {
        								elements.add(javaElement);
        							}
        						}        						
        					}
        				}
        			}
        		}
        	}
        }

        if (!elements.isEmpty())
        {
            Job checkerJob;
            String customClasses = CheckerPlugin.getDefault()
                    .getPreferenceStore()
                    .getString(CheckerPreferences.PREF_CHECKER_CUSTOM_CLASSES);

            // Depending on how this runner was created, we will either:
            // * just run one particular checker
            // * use the custom configured checkers
            // * run "selected" checkers using the action or auto build

            final String actualNames;

            if (!usePrefs && !useCustom && !useSingleCustom) {
            	actualNames = checkerName;
            }
            else if (!usePrefs && !useSingleCustom) {
            	actualNames = customClasses;
            }
            else if(useSingleCustom) {
            	actualNames = event.getParameter("checker-framework-eclipse-plugin.checker");
            } else {
                List<String> names = getClassNameFromPrefs();
                actualNames = PluginUtil.join(",", names);
            }

            checkerJob = new CheckerWorker(elements, actualNames, hasQuals);

            checkerJob.setUser(true);
            checkerJob.setPriority(Job.BUILD);
            checkerJob.setRule(new MutexSchedulingRule());
            checkerJob.schedule();
        }

        return null;
    }
}
