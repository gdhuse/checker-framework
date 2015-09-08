package org.checkerframework.eclipse.builder;

import java.util.HashSet;
import java.util.Set;

import org.checkerframework.eclipse.util.Util;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

public class CheckerResourceVisitor implements IResourceDeltaVisitor
{
    private HashSet<IJavaElement> buildFiles;

    CheckerResourceVisitor()
    {
        buildFiles = new HashSet<IJavaElement>();
    }

    @Override
    public boolean visit(IResourceDelta delta) throws CoreException
    {
        // if the file has been removed, we don't need to visit
        // its children or process it any further
        if (delta.getKind() == IResourceDelta.REMOVED)
        {
            return false;
        }
        else if (Util.isJavaFile(delta.getResource()))
        {
        	IJavaElement javaElement = JavaCore.create(delta.getResource());
        	if(javaElement != null) {
        		buildFiles.add(javaElement);
        	}
        }

        return true;
    }

    public Set<IJavaElement> getBuildFiles()
    {
    	return buildFiles;
    }
}
