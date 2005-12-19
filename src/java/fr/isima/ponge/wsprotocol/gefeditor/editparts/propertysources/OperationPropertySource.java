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

package fr.isima.ponge.wsprotocol.gefeditor.editparts.propertysources;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.views.properties.ComboBoxPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.TextPropertyDescriptor;

import fr.isima.ponge.wsprotocol.OperationKind;
import fr.isima.ponge.wsprotocol.Polarity;
import fr.isima.ponge.wsprotocol.gefeditor.Messages;
import fr.isima.ponge.wsprotocol.impl.MessageImpl;
import fr.isima.ponge.wsprotocol.impl.OperationImpl;

/**
 * The property source for operations (actually works on the message carried by the operation).
 * 
 * @author Julien Ponge (ponge@isima.fr)
 */
public class OperationPropertySource implements IPropertySource
{

    /**
     * Id for the WSDL problems resource marker.
     */
    private static final String WSDL_PROBLEM_MARKER_ID = "gef.editor.wsdl.io.problem";

    /**
     * The message polarity property name.
     */
    protected static final String MESSAGE_POLARITY_PROPERTY = "message.polarity"; //$NON-NLS-1$

    /**
     * The message name property name.
     */
    protected static final String MESSAGE_NAME_PROPERTY = "message.name"; //$NON-NLS-1$
    
    /**
     * The operation kind property name.
     */
    protected static final String OPERATION_KIND_PROPERTY = "operation.kind"; //$NON-NLS-1$

    /**
     * The operation
     */
    protected OperationImpl operation;
    
    /**
     * The message.
     */
    protected MessageImpl message;

    /**
     * The (optional) WSDL document location.
     */
    protected String wsdlLocation;

    /**
     * The associated protocol file descriptor.
     */
    protected IFile protocolFile;

    /**
     * WSDL messages, as an array.
     */
    protected String[] wsdlMessages;

    /**
     * WSDL messages, as a list.
     */
    protected List wsdlMessagesList;

    /**
     * The properties descriptors.
     */
    protected IPropertyDescriptor[] propertyDescriptors = new IPropertyDescriptor[3];

    /**
     * Constructs a new operations properties source.
     * 
     * @param operation
     *            The operation.
     * @param wsdlLocation
     *            The WSDL document location.
     * @param protocolFile
     *            The associated protocol file.
     */
    public OperationPropertySource(OperationImpl operation, String wsdlLocation, IFile protocolFile)
    {
        super();
        this.operation = operation;
        this.message = (MessageImpl)operation.getMessage();
        this.wsdlLocation = wsdlLocation;
        this.protocolFile = protocolFile;

        updatePropertyDescriptors();
    }

    /**
     * Creates or updates the property descriptors. If the protocol has a WSDL that can be parsed,
     * then the messages names will be presented in a combo.
     */
    protected void updatePropertyDescriptors()
    {
        List messages = getWSDLMessagesNames();
        if (messages.equals(Collections.EMPTY_LIST) || message.getPolarity().equals(Polarity.NULL))
        {
            propertyDescriptors[0] = new TextPropertyDescriptor(MESSAGE_NAME_PROPERTY,
                    Messages.messageName);
        }
        else
        {
            Object[] vals = messages.toArray();
            wsdlMessages = new String[vals.length];
            for (int i = 0; i < vals.length; ++i)
            {
                wsdlMessages[i] = (String) vals[i];
            }
            wsdlMessagesList = messages;
            propertyDescriptors[0] = new ComboBoxPropertyDescriptor(MESSAGE_NAME_PROPERTY,
                    Messages.messageName, wsdlMessages);
        }

        propertyDescriptors[1] = new ComboBoxPropertyDescriptor(MESSAGE_POLARITY_PROPERTY,
                Messages.messagePolarity, new String[] { Messages.input, Messages.output,
                        Messages.none });
        propertyDescriptors[2] = new ComboBoxPropertyDescriptor(OPERATION_KIND_PROPERTY, Messages.operationKind, new String[] {
           Messages.explicit, Messages.implicit   
        });
    }

    /*
     * Used to cache the WSDL parsing results.
     */
    private static Map cachedParsings = new HashMap();

    /**
     * Trys to get the messages names from a WSDL document.
     * 
     * @return The list of the messages names or <code>Collections.EMPTY_LIST</code>.
     */
    protected List getWSDLMessagesNames()
    {
        // No WSDL
        if (wsdlLocation == null || "".equals(wsdlLocation))
        {
            return Collections.EMPTY_LIST;
        }

        // Cached parsings
        if (cachedParsings.containsKey(wsdlLocation))
        {
            return (List) cachedParsings.get(wsdlLocation);
        }

        // Parse the WSDL
        cleanWSDLProblemMarker();
        List names = new ArrayList();
        try
        {
            SAXReader reader = new SAXReader();
            Document document = reader.read(new URL(wsdlLocation));
            Iterator nodesIt = document.selectNodes("//*[local-name()='message']/@name").iterator();
            while (nodesIt.hasNext())
            {
                Attribute attr = (Attribute) nodesIt.next();
                names.add(attr.getValue());
            }
            Collections.sort(names);
        }
        catch (MalformedURLException e)
        {
            reportWSDLProblem();
        }
        catch (DocumentException e)
        {
            reportWSDLProblem();
        }
        cachedParsings.put(wsdlLocation, (names.size() > 0) ? names : Collections.EMPTY_LIST);
        return (names.size() > 0) ? names : Collections.EMPTY_LIST;
    }

    /**
     * Removes the WSDL problem marker on the file resource (if any).
     */
    private void cleanWSDLProblemMarker()
    {
        try
        {
            IMarker[] problems = protocolFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ONE);
            for (int i = 0; i < problems.length; ++i)
            {
                if (problems[i].exists() && problems[i].getAttributes().containsKey(WSDL_PROBLEM_MARKER_ID))
                {
                    problems[i].delete();
                }
            }
        }
        catch (CoreException e)
        {
            e.printStackTrace();
        }        
    }

    /**
     * Reports a WSDL problem on the file resource.
     */
    private void reportWSDLProblem()
    {
        try
        {
            IMarker marker = protocolFile.createMarker(IMarker.PROBLEM);
            if (marker.exists())
            {
                marker.setAttribute(IMarker.TRANSIENT, true);
                marker.setAttribute(IMarker.MESSAGE, "The specified WSDL URL is invalid or unreachable.");
                marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
                marker.setAttribute(WSDL_PROBLEM_MARKER_ID, WSDL_PROBLEM_MARKER_ID);
            }
        }
        catch (CoreException e)
        {
            e.printStackTrace();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.views.properties.IPropertySource#getEditableValue()
     */
    public Object getEditableValue()
    {
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.views.properties.IPropertySource#getPropertyDescriptors()
     */
    public IPropertyDescriptor[] getPropertyDescriptors()
    {
        return propertyDescriptors;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.views.properties.IPropertySource#getPropertyValue(java.lang.Object)
     */
    public Object getPropertyValue(Object id)
    {
        if (id.equals(MESSAGE_NAME_PROPERTY))
        {
            if (wsdlMessages == null)
            {
                return message.getName();
            }
            else
            {
                int index = wsdlMessagesList.indexOf(message.getName());
                if (index == -1)
                {
                    index = 0;
                    message.setName((String) wsdlMessagesList.get(index));
                }
                return new Integer(index);
            }
        }
        else if (id.equals(MESSAGE_POLARITY_PROPERTY))
        {
            Polarity p = message.getPolarity();
            if (p.equals(Polarity.POSITIVE))
            {
                return new Integer(0);
            }
            else if (p.equals(Polarity.NEGATIVE))
            {
                return new Integer(1);
            }
            else if (p.equals(Polarity.NULL))
            {
                return new Integer(2);
            }
        }
        else if (id.equals(OPERATION_KIND_PROPERTY))
        {
            OperationKind kind = operation.getOperationKind();
            if (kind.equals(OperationKind.EXPLICIT))
            {
                return new Integer(0);
            }
            else if (kind.equals(OperationKind.IMPLICIT))
            {
                return new Integer(1);
            }
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.views.properties.IPropertySource#isPropertySet(java.lang.Object)
     */
    public boolean isPropertySet(Object id)
    {
        // All properties are mandatory in the model
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.views.properties.IPropertySource#resetPropertyValue(java.lang.Object)
     */
    public void resetPropertyValue(Object id)
    {
        // Nothing to do here ...
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.views.properties.IPropertySource#setPropertyValue(java.lang.Object,
     *      java.lang.Object)
     */
    public void setPropertyValue(Object id, Object value)
    {
        if (id.equals(MESSAGE_NAME_PROPERTY))
        {
            if (wsdlMessages == null)
            {
                message.setName((String) value);
            }
            else
            {
                message.setName((String) wsdlMessagesList.get(((Integer) value).intValue()));
            }
        }
        else if (id.equals(MESSAGE_POLARITY_PROPERTY))
        {
            if (value.equals(Integer.valueOf("0"))) //$NON-NLS-1$
            {
                message.setPolarity(Polarity.POSITIVE);
            }
            else if (value.equals(Integer.valueOf("1"))) //$NON-NLS-1$
            {
                message.setPolarity(Polarity.NEGATIVE);
            }
            else if (value.equals(Integer.valueOf("2"))) //$NON-NLS-1$
            {
                message.setPolarity(Polarity.NULL);
            }
            //updatePropertyDescriptors();
        }
        else if (id.equals(OPERATION_KIND_PROPERTY))
        {
            if (value.equals(Integer.valueOf("0"))) //$NON-NLS-1$
            {
                operation.setOperationKind(OperationKind.EXPLICIT);
            }
            else if (value.equals(Integer.valueOf("1"))) //$NON-NLS-1$
            {
                operation.setOperationKind(OperationKind.IMPLICIT);
                message.setPolarity(Polarity.NULL);
            }
        }
    }

}
