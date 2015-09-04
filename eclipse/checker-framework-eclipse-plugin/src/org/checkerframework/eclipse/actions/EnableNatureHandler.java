package org.checkerframework.eclipse.actions;

import java.util.List;

import org.checkerframework.eclipse.CheckerPlugin;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.viewers.ISelection;

public class EnableNatureHandler extends ProjectNatureHandler
{
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        ISelection selection = getSelection(event);
        List<IJavaProject> projects = selectionToJavaProjects(selection);

        try
        {
            for(IJavaProject project : projects) {
	            IProjectDescription desc = project.getProject().getDescription();
	            String[] natures = desc.getNatureIds();
	            boolean hasNature = hasNature(natures);
	
	            if (!hasNature)
	                setNature(project.getProject(), desc, natures);
            }

        }catch (CoreException e)
        {
            CheckerPlugin.logException(e, e.getMessage());
        }

        return null;
    }
}
