/*
 * Copyright (c) 2005 Julien Ponge - All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fr.isima.ponge.wsprotocol.gefeditor.wizards;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWizard;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import fr.isima.ponge.wsprotocol.BusinessProtocol;
import fr.isima.ponge.wsprotocol.BusinessProtocolFactory;
import fr.isima.ponge.wsprotocol.gefeditor.Messages;
import fr.isima.ponge.wsprotocol.impl.BusinessProtocolFactoryImpl;
import fr.isima.ponge.wsprotocol.xml.XmlIOManager;

/**
 * The wizard to create a new (empty) business protocol file. This wizard is a small modification of
 * the code automatically generated by Eclipse.
 * 
 * @author Julien Ponge (ponge@isima.fr)
 */
public class NewBusinessProtocolWizard extends Wizard implements INewWizard
{
    private NewBusinessProtocolWizardPage page;

    private ISelection selection;

    /**
     * Constructor for NewBusinessProtocolWizard.
     */
    public NewBusinessProtocolWizard()
    {
        super();
        setNeedsProgressMonitor(true);
    }

    /**
     * Adding the page to the wizard.
     */

    public void addPages()
    {
        page = new NewBusinessProtocolWizardPage(selection);
        addPage(page);
    }

    /**
     * This method is called when 'Finish' button is pressed in the wizard. We will create an
     * operation and run it using wizard as execution context.
     */
    public boolean performFinish()
    {
        final String containerName = page.getContainerName();
        final String fileName = page.getFileName();
        IRunnableWithProgress op = new IRunnableWithProgress() {
            public void run(IProgressMonitor monitor) throws InvocationTargetException
            {
                try
                {
                    doFinish(containerName, fileName, monitor);
                }
                catch (CoreException e)
                {
                    throw new InvocationTargetException(e);
                }
                finally
                {
                    monitor.done();
                }
            }
        };
        try
        {
            getContainer().run(true, false, op);
        }
        catch (InterruptedException e)
        {
            return false;
        }
        catch (InvocationTargetException e)
        {
            Throwable realException = e.getTargetException();
            MessageDialog.openError(getShell(), Messages.error, realException.getMessage());
            return false;
        }
        return true;
    }

    /**
     * The worker method. It will find the container, create the file if missing or just replace its
     * contents, and open the editor on the newly created file.
     */

    private void doFinish(String containerName, String fileName, IProgressMonitor monitor)
            throws CoreException
    {
        // create a sample file
        monitor.beginTask(Messages.creatingTask + fileName, 2);
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IResource resource = root.findMember(new Path(containerName));
        if (!resource.exists() || !(resource instanceof IContainer))
        {
            throwCoreException(Messages.containerException1 + containerName + Messages.containerException2);
        }
        IContainer container = (IContainer) resource;
        final IFile file = container.getFile(new Path(fileName));
        try
        {
            InputStream stream = openContentStream();
            if (file.exists())
            {
                file.setContents(stream, true, true, monitor);
            }
            else
            {
                file.create(stream, true, monitor);
            }
            stream.close();
        }
        catch (IOException e)
        {
        }
        monitor.worked(1);
        monitor.setTaskName(Messages.openingFileForEditingTask);
        getShell().getDisplay().asyncExec(new Runnable() {
            public void run()
            {
                IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
                        .getActivePage();
                try
                {
                    IDE.openEditor(page, file, true);
                }
                catch (PartInitException e)
                {
                }
            }
        });
        monitor.worked(1);
    }

    /**
     * We will initialize file contents with a sample text.
     * 
     * @throws IOException
     */

    private InputStream openContentStream() throws IOException
    {
        BusinessProtocolFactory factory = new BusinessProtocolFactoryImpl();
        BusinessProtocol protocol = factory.createBusinessProtocol(Messages.newProtocolModelName);
        XmlIOManager manager = new XmlIOManager(factory);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out);
        manager.writeBusinessProtocol(protocol, writer);
        writer.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void throwCoreException(String message) throws CoreException
    {
        IStatus status = new Status(IStatus.ERROR, "fr.isima.ponge.wsprotocol.gefeditor", //$NON-NLS-1$
                IStatus.OK, message, null);
        throw new CoreException(status);
    }

    /**
     * We will accept the selection in the workbench to see if we can initialize from it.
     * 
     * @see IWorkbenchWizard#init(IWorkbench, IStructuredSelection)
     */
    public void init(IWorkbench workbench, IStructuredSelection selection)
    {
        this.selection = selection;
    }
}
