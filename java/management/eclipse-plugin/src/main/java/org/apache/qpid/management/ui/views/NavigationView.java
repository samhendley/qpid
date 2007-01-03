/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.management.ui.views;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.qpid.management.ui.ApplicationRegistry;
import org.apache.qpid.management.ui.Constants;
import org.apache.qpid.management.ui.ManagedBean;
import org.apache.qpid.management.ui.ManagedServer;
import org.apache.qpid.management.ui.ServerRegistry;
import org.apache.qpid.management.ui.exceptions.InfoRequiredException;
import org.apache.qpid.management.ui.exceptions.ManagementConsoleException;
import org.apache.qpid.management.ui.jmx.JMXServerRegistry;
import org.apache.qpid.management.ui.jmx.MBeanUtility;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.ViewPart;

/**
 * Navigation View for navigating the managed servers and managed beans on
 * those servers
 * @author Bhupendra Bhardwaj
 */
public class NavigationView extends ViewPart
{
	public static final String ID = "org.apache.qpid.management.ui.navigationView";    
    public static final String INI_FILENAME = System.getProperty("user.home") + File.separator + "qpidManagementConsole.ini";
    
    private TreeViewer _treeViewer = null;
    private TreeObject _rootNode = null;
    private TreeObject _serversRootNode = null;
    // Map of connected servers
    private HashMap<ManagedServer, TreeObject> _managedServerMap = new HashMap<ManagedServer, TreeObject>();
    
    private void createTreeViewer(Composite parent)
    {
        _treeViewer = new TreeViewer(parent);
        _treeViewer.setContentProvider(new ContentProviderImpl());
        _treeViewer.setLabelProvider(new LabelProviderImpl());        
        _treeViewer.setSorter(new ViewerSorterImpl());
        
        // layout the tree viewer below the label field, to cover the area
        GridData layoutData = new GridData();
        layoutData = new GridData();
        layoutData.grabExcessHorizontalSpace = true;
        layoutData.grabExcessVerticalSpace = true;
        layoutData.horizontalAlignment = GridData.FILL;
        layoutData.verticalAlignment = GridData.FILL;
        _treeViewer.getControl().setLayoutData(layoutData);
        _treeViewer.setUseHashlookup(true);
        
        createListeners();
    }
    
    /**
     * Creates listeners for the JFace treeviewer
     */
    private void createListeners()
    {
        _treeViewer.addDoubleClickListener(new IDoubleClickListener()
            {
                public void doubleClick(DoubleClickEvent event)
                {
                    IStructuredSelection ss = (IStructuredSelection)event.getSelection();
                    if (ss == null || ss.getFirstElement() == null)
                    {
                        return;
                    }
                    boolean state = _treeViewer.getExpandedState(ss.getFirstElement());
                    _treeViewer.setExpandedState(ss.getFirstElement(), !state);
                }
            });
        
        _treeViewer.addTreeListener(new ITreeViewerListener()
        {
            public void treeExpanded(TreeExpansionEvent event)
            {
                _treeViewer.setExpandedState(event.getElement(), true);
                // Following will cause the selection event to be sent, so commented
                //_treeViewer.setSelection(new StructuredSelection(event.getElement()));
                _treeViewer.refresh();
            }

            public void treeCollapsed(TreeExpansionEvent event)
            {
                _treeViewer.setExpandedState(event.getElement(), false);
                _treeViewer.refresh();
            }
        });
    }   
    
    /**
     * Creates Qpid Server connection using JMX RMI protocol
     * @param server
     * @throws Exception
     */
    private void createRMIServerConnection(ManagedServer server) throws Exception
    {     
        try
        {
            // Currently Qpid Management Console only supports JMX MBeanServer
            ServerRegistry serverRegistry = new JMXServerRegistry(server);  
            ApplicationRegistry.addServer(server, serverRegistry);         
        }
        catch(Exception ex)
        {
            throw new Exception("Error in connecting to Qpid broker at " + server.getUrl(), ex);
        }
    }
    
    private String getRMIURL(String host)
    {
        return "service:jmx:rmi:///jndi/rmi://" + host + "/jmxrmi";
    }
    
    /**
     * Adds a new server node in the navigation view if server connection is successful.
     * @param transportProtocol
     * @param host
     * @param port
     * @param domain
     * @throws Exception
     */
    public void addNewServer(String transportProtocol, String host, String port, String domain)
        throws Exception
    {
        String serverAddress = host + ":" + port;
        String url = null;
        ManagedServer managedServer = null;
        
        if ("RMI".equals(transportProtocol))
        {            
            url = getRMIURL(serverAddress);
            List<TreeObject> list = _serversRootNode.getChildren();
            for (TreeObject node : list)
            {
                if (url.equals(node.getUrl()))
                    throw new InfoRequiredException("Server " + serverAddress + " is already added");
            }
            
            managedServer = new ManagedServer(url, domain);
            managedServer.setName(serverAddress);
            createRMIServerConnection(managedServer);
        }
        else
        {
            throw new InfoRequiredException(transportProtocol + " transport is not supported");
        }
        
        // Server connection is successful. Now add the server in the tree
        TreeObject serverNode = new TreeObject(serverAddress, Constants.SERVER);
        serverNode.setUrl(url);
        serverNode.setManagedObject(managedServer);
        _serversRootNode.addChild(serverNode);
        
        // Add server in the connected server map
        _managedServerMap.put(managedServer, serverNode);
        populateServer(serverNode); 
        _treeViewer.refresh();
        
        // save server address in file            
        addServerAddressInFile(serverAddress);
    }
    
    /**
     * Server addresses are stored in a file. When user launches the application again, the
     * server addresses are picked up from the file and shown in the navigfation view. This method
     * adds the server address in a file, when a new server is added in the navigation view. 
     * @param serverAddress
     */
    private void addServerAddressInFile(String serverAddress)
    {
        File file = new File(INI_FILENAME);
        try
        {
            if (!file.exists())
                file.createNewFile();
            
            BufferedWriter out = new BufferedWriter(new FileWriter(file, true));
            out.write(serverAddress + "\n");
            out.close();

        }
        catch(Exception ex)
        {
            System.out.println("Could not write to the file " + INI_FILENAME);
            System.out.println(ex);
        }
    }    

    /**
     * Queries the qpid server for MBeans and populates the navigation view with all MBeans for 
     * the given server node.
     * @param serverNode
     */
    private void populateServer(TreeObject serverNode)
    {
        ManagedServer server = (ManagedServer)serverNode.getManagedObject();
        String domain = server.getDomain();
        try
        {
            if (!domain.equals("All"))
            {
                TreeObject domainNode = new TreeObject(domain, Constants.DOMAIN);
                domainNode.setParent(serverNode);

                populateDomain(domainNode); 
            }
            else
            {
                List<TreeObject> domainList = new ArrayList<TreeObject>();
                List<String> domains = MBeanUtility.getAllDomains(server);;           
                for (String domainName : domains)
                {       
                    TreeObject domainNode = new TreeObject(domainName, Constants.DOMAIN);
                    domainNode.setParent(serverNode);

                    domainList.add(domainNode);
                    populateDomain(domainNode);               
                }
            }
        }
        catch(Exception ex)
        {
            System.out.println("\nError in connecting to Qpid broker ");
            System.out.println("\n" + ex.toString());
        }
    }
    
    /**
     * Queries the Qpid Server and populates the given domain node with all MBeans undser that domain.
     * @param domain
     * @throws IOException
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private void populateDomain(TreeObject domain) throws IOException, Exception
    {
        ManagedServer server = (ManagedServer)domain.getParent().getManagedObject();
        
        // Add these three types - Connection, Exchange, Queue
        // By adding these, these will always be available, even if there are no mbeans under thse types
        // This is required because, the mbeans will be added from mbeanview, by selecting from the list
        TreeObject typeChild = new TreeObject(Constants.CONNECTION, Constants.TYPE);
        typeChild.setParent(domain);
        typeChild = new TreeObject(Constants.EXCHANGE, Constants.TYPE);
        typeChild.setParent(domain);
        typeChild = new TreeObject(Constants.QUEUE, Constants.TYPE);
        typeChild.setParent(domain);
        
        
        // Now populate the mbenas under those types
        List<ManagedBean> mbeans = MBeanUtility.getManagedObjectsForDomain(server, domain.getName());
        for (ManagedBean mbean : mbeans)
        {
            mbean.setServer(server);
            ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(server);
            serverRegistry.addManagedObject(mbean);     
            
            // Add all mbeans other than Connections, Exchanges and Queues. Because these will be added
            // manually by selecting from MBeanView
            if (!(mbean.getType().equals(Constants.CONNECTION) || mbean.getType().equals(Constants.EXCHANGE) || mbean.getType().equals(Constants.QUEUE)))
            {
                addManagedBean(domain, mbean);
            }
        }
    }
    
    /**
     * Checks if a particular mbeantype is already there in the navigation view for a domain.
     * This is used while populating domain with mbeans.
     * @param domain
     * @param typeName
     * @return Node if given mbeantype already exists, otherwise null
     */
    private TreeObject getMBeanTypeNode(TreeObject domain, String typeName)
    {
        List<TreeObject> childNodes = domain.getChildren();
        
        for (TreeObject child : childNodes)
        {
            if (Constants.TYPE.equals(child.getType()) && typeName.equals(child.getName()))
                return child;
        }
        return null;
    }
    
    private boolean doesMBeanNodeAlreadyExist(TreeObject typeNode, String mbeanName)
    {
        List<TreeObject> childNodes = typeNode.getChildren();
        for (TreeObject child : childNodes)
        {
            if (Constants.MBEAN.equals(child.getType()) && mbeanName.equals(child.getName()))
                return true;
        }
        return false;
    }
    
    /**
     * Adds the given MBean to the given domain node. Creates Notification node for the MBean.
     * @param domain
     * @param mbean mbean
     */
    private void addManagedBean(TreeObject domain, ManagedBean mbean) throws Exception
    {
        String type = mbean.getType();
        String name = mbean.getName();

        TreeObject typeNode = getMBeanTypeNode(domain, type);
        if (typeNode != null && doesMBeanNodeAlreadyExist(typeNode, name))
            return;
        
        TreeObject mbeanNode = null;
        if (typeNode != null) // type node already exists
        {
            if (name == null)
            {
                throw new ManagementConsoleException("Two mbeans can't exist without a name and with same type");
            }
            mbeanNode = new TreeObject(mbean);
            mbeanNode.setParent(typeNode);
        }
        else
        {
            // type node does not exist. Now check if node to be created as mbeantype or MBean
            if (name != null)  // A managedObject with type and name
            {
                typeNode = new TreeObject(type, Constants.TYPE);
                typeNode.setParent(domain);
                mbeanNode = new TreeObject(mbean);
                mbeanNode.setParent(typeNode);               
            }
            else              // A managedObject with only type
            {
                mbeanNode = new TreeObject(mbean);
                mbeanNode.setParent(domain);
            }
        }
        
        // Add notification node
        // TODO: show this only if the mbean sends any notification
        TreeObject notificationNode = new TreeObject(Constants.NOTIFICATION, Constants.NOTIFICATION);
        notificationNode.setParent(mbeanNode);
    }
    
    /**
     * Removes all the child nodes of the given parent node
     * @param parent
     */
    private void removeManagedObject(TreeObject parent)
    {
        List<TreeObject> list = parent.getChildren();
        for (TreeObject child : list)
        {
            removeManagedObject(child);
        }
        
        list.clear();
    }

    /**
     * Removes the mbean from the tree
     * @param parent
     * @param mbean
     */
    private void removeManagedObject(TreeObject parent, ManagedBean mbean)
    {
        List<TreeObject> list = parent.getChildren();
        TreeObject objectToRemove = null;
        for (TreeObject child : list)
        {
            if (Constants.MBEAN.equals(child.getType()))
            {
                String name = mbean.getName() != null ? mbean.getName() : mbean.getType();
                if (child.getName().equals(name))
                {
                    objectToRemove = child;
                    break;
                }
            }
            else
            {
                removeManagedObject(child, mbean);
            }
        }
        
        if (objectToRemove != null)
        {
            list.remove(objectToRemove);
        }
        
    }
    
    /**
     * Closes the Qpid server connection
     */
    public void disconnect() throws Exception
    {
        TreeObject selectedNode = getSelectedServerNode();        
        ManagedServer managedServer = (ManagedServer)selectedNode.getManagedObject();
        if (!_managedServerMap.containsKey(managedServer))
            return;

        // Close server connection
        ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(managedServer);
        if (serverRegistry == null)  // server connection is already closed
            return;
        
        serverRegistry.closeServerConnection();
        // Add server to the closed server list and the worker thread will remove the server from required places.
        ApplicationRegistry.serverConnectionClosed(managedServer);
    }
    
    /**
     * Connects the selected server node
     * @throws Exception
     */
    public void reconnect() throws Exception
    {
        TreeObject selectedNode = getSelectedServerNode();
        ManagedServer managedServer = (ManagedServer)selectedNode.getManagedObject();
        if(_managedServerMap.containsKey(managedServer))
        {
            throw new InfoRequiredException("Server " + managedServer.getName() + " is already connected");
        }           
        createRMIServerConnection(managedServer);
        _managedServerMap.put(managedServer, selectedNode);
        populateServer(selectedNode);
        _treeViewer.refresh();  
    }
    
    /**
     * Closes the Qpid server connection if not already closed and removes the server node from the navigation view and
     * also from the ini file stored in the system.
     * @throws Exception
     */
    public void removeServer() throws Exception
    {
        disconnect();
        
        // Remove from the Tree
        String serverNodeName = getSelectedServerNode().getName();        
        List<TreeObject> list = _serversRootNode.getChildren();
        TreeObject objectToRemove = null;
        for (TreeObject child : list)
        {
            if (child.getName().equals(serverNodeName))
            {
                objectToRemove = child;
                break;
            }
        }
        
        if (objectToRemove != null)
        {
            list.remove(objectToRemove);
        }
        
        _treeViewer.refresh();
        
        // Remove from the ini file
        List<String> serversList = getServerListFromFile();
        serversList.remove(serverNodeName);
        
        BufferedWriter out = new BufferedWriter(new FileWriter(INI_FILENAME));
        for (String serverAddress : serversList)
        {
            out.write(serverAddress + "\n");
        }
        out.close();
    }
    
    /**
     * @return the server addresses from the ini file
     * @throws Exception
     */
    private List<String> getServerListFromFile() throws Exception
    {
        BufferedReader in = new BufferedReader(new FileReader(INI_FILENAME));
        List<String> serversList = new ArrayList<String>();
        String str;
        while ((str = in.readLine()) != null)
        {
            serversList.add(str);
        }
        in.close();
        
        return serversList;
    }
    
    private TreeObject getSelectedServerNode() throws Exception
    {
        IStructuredSelection ss = (IStructuredSelection)_treeViewer.getSelection();
        TreeObject selectedNode = (TreeObject)ss.getFirstElement();
        if (ss.isEmpty() || selectedNode == null || (!selectedNode.getType().equals(Constants.SERVER)))
        {
            throw new InfoRequiredException("Please select the server");
        }

        return selectedNode;
    }
	/**
     * This is a callback that will allow us to create the viewer and initialize
     * it.
     */
	public void createPartControl(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.marginHeight = 2;
        gridLayout.marginWidth = 2;
        gridLayout.horizontalSpacing = 0; 
        gridLayout.verticalSpacing = 2;        
        composite.setLayout(gridLayout);
        
        createTreeViewer(composite);
        _rootNode = new TreeObject("ROOT", "ROOT");
        _serversRootNode = new TreeObject(Constants.NAVIGATION_ROOT, "ROOT");
        _serversRootNode.setParent(_rootNode);
        
        _treeViewer.setInput(_rootNode);
        // set viewer as selection event provider for MBeanView
        getSite().setSelectionProvider(_treeViewer);      
        
        // Start worker thread to refresh tree for added or removed objects
        (new Thread(new Worker())).start();     
        
        try
        {
            // load the list of servers already added from file
            List<String> serversList = getServerListFromFile();
            for (String serverAddress : serversList)
            {
                try
                {
                    String url = getRMIURL(serverAddress);
                    ManagedServer managedServer = new ManagedServer(url, "org.apache.qpid");
                    managedServer.setName(serverAddress);
                    TreeObject serverNode = new TreeObject(serverAddress, Constants.SERVER);
                    serverNode.setUrl(url);
                    serverNode.setManagedObject(managedServer);
                    _serversRootNode.addChild(serverNode);
                }
                catch(Exception ex)
                {
                    System.out.println(ex);
                }
            }
            _treeViewer.refresh();
        }
        catch(Exception ex)
        {
            System.out.println(ex);
        }
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus()
    {

	}
    
    public void refresh()
    {
        _treeViewer.refresh();
    }
    
    /**
     * Content provider class for the tree viewer
     */
    private class ContentProviderImpl implements ITreeContentProvider
    {
        public Object[] getElements(Object parent)
        {
            return getChildren(parent);
        }
        
        public Object[] getChildren(final Object parentElement)
        {
            final TreeObject node = (TreeObject)parentElement;
            return node.getChildren().toArray(new TreeObject[0]);
        }
        
        public Object getParent(final Object element)
        {
            final TreeObject node = (TreeObject)element;
            return node.getParent();
        }
        
        public boolean hasChildren(final Object element)
        {
            final TreeObject node = (TreeObject) element;
            return !node.getChildren().isEmpty();
        }
        
        public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput)
        {
            // Do nothing
        }
        
        public void dispose()
        {
            // Do nothing
        }
    }
    
    /**
     * Label provider class for the tree viewer
     */
    private class LabelProviderImpl extends LabelProvider implements IFontProvider
    {
        public Image getImage(Object element)
        {
            TreeObject node = (TreeObject)element;
            if (node.getType().equals(Constants.NOTIFICATION))
            {
                return ApplicationRegistry.getImage(Constants.NOTIFICATION_IMAGE);
            }
            else if (!node.getType().equals(Constants.MBEAN))
            {
               if (_treeViewer.getExpandedState(node))
                   return ApplicationRegistry.getImage(Constants.OPEN_FOLDER_IMAGE);
               else
                   return ApplicationRegistry.getImage(Constants.CLOSED_FOLDER_IMAGE);
                   
            }
            else
            {
                return ApplicationRegistry.getImage(Constants.MBEAN_IMAGE);
            }
        }
        
        public String getText(Object element)
        {
            TreeObject node = (TreeObject)element;
            return node.getName();
        }
        
        public Font getFont(Object element)
        {
            TreeObject node = (TreeObject)element;
            if (node.getType().equals(Constants.SERVER))
            {
                if (node.getChildren().isEmpty())
                    return ApplicationRegistry.getFont(Constants.FONT_NORMAL);
                else
                    return ApplicationRegistry.getFont(Constants.FONT_BOLD);
            }
            return ApplicationRegistry.getFont(Constants.FONT_NORMAL);
        }
    } // End of LabelProviderImpl
    
    
    private class ViewerSorterImpl extends ViewerSorter
    {
        public int category(Object element)
        {
            TreeObject node = (TreeObject)element;
            if (node.getType().equals(Constants.MBEAN))
                return 1;
            return 2;
        }
    }
            
    /**
     * Worker thread, which keeps looking for new ManagedObjects to be added and 
     * unregistered objects to be removed from the tree.
     * @author Bhupendra Bhardwaj
     */
    private class Worker implements Runnable
    {
        public void run()
        {
            while(true)
            {
                if (_managedServerMap.isEmpty())
                    continue;
                
                try
                {
                    Thread.sleep(2000);
                }
                catch(Exception ex)
                {

                }                          
                refreshRemovedObjects();                               
                refreshClosedServerConnections();                                
            }// end of while loop
        }// end of run method.        
    }// end of Worker class
    
    public void addManagedBean(ManagedBean mbean) throws Exception
    {
        TreeObject treeServerObject = _managedServerMap.get(mbean.getServer());
        List<TreeObject> domains = treeServerObject.getChildren();
        TreeObject domain = null;
        for (TreeObject child : domains)
        {
            if (child.getName().equals(mbean.getDomain()))
            {
                domain = child;
                break;
            }
        }
        
        addManagedBean(domain, mbean);
        _treeViewer.refresh();
    }
    
    private void refreshRemovedObjects()
    {
        for (ManagedServer server : _managedServerMap.keySet())
        {
            final ServerRegistry serverRegistry = ApplicationRegistry.getServerRegistry(server);
            if (serverRegistry == null)  // server connection is closed
                continue;
            
            final List<ManagedBean> removalList = serverRegistry.getObjectsToBeRemoved();
            if (removalList != null)
            {
                Display display = getSite().getShell().getDisplay();
                display.syncExec(new Runnable()
                    {
                        public void run()
                        {
                            for (ManagedBean mbean : removalList)
                            {
                                System.out.println("removing  " + mbean.getName() + " " + mbean.getType());
                                TreeObject treeServerObject = _managedServerMap.get(mbean.getServer());
                                List<TreeObject> domains = treeServerObject.getChildren();
                                TreeObject domain = null;
                                for (TreeObject child : domains)
                                {
                                    if (child.getName().equals(mbean.getDomain()))
                                    {
                                        domain = child;
                                        break;
                                    }
                                }
                                removeManagedObject(domain, mbean);
                                serverRegistry.removeManagedObject(mbean);
                            }
                            _treeViewer.refresh();
                        }
                    });
            }
        }
    }
    
    /**
     * Gets the list of closed server connection from the ApplicationRegistry and then removes
     * the closed server nodes from the navigation view
     */
    private void refreshClosedServerConnections()
    {
        final List<ManagedServer> closedServers = ApplicationRegistry.getClosedServers();
        if (closedServers != null)
        {
            Display display = getSite().getShell().getDisplay();
            display.syncExec(new Runnable()
            {
                public void run()
                {
                    for (ManagedServer server : closedServers)
                    {
                        removeManagedObject(_managedServerMap.get(server));
                        _managedServerMap.remove(server);
                        ApplicationRegistry.removeServer(server);
                    }
                    
                    _treeViewer.refresh();
                }
            });
        }
    }
    
}