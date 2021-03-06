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
package org.apache.qpid.jndi.referenceable;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Looksup a reference from a JNDI source.
 * Given a properties file with the JNDI information and a binding string.
 */
public class Lookup
{
    private static final String USAGE = "USAGE: java lookup <JNDI Properties file> -b <binding>";
    private Properties _properties;
    private Object _object;

    public Lookup(String propertiesFile, String bindingValue) throws NamingException
    {
        // Set up the environment for creating the initial context
        String qpid_home = System.getProperty("QPID_HOME");

        if (qpid_home == null || qpid_home.equals(""))
        {
            System.out.println("QPID_HOME is not set");
            System.exit(1);
        }

        if (qpid_home.charAt(qpid_home.length() - 1) != '/')
        {
            qpid_home += "/";
        }

        try
        {
            InputStream inputStream = new FileInputStream(qpid_home + propertiesFile);
            Properties properties = new Properties();
            properties.load(inputStream);

            _properties = properties;
            lookup(bindingValue);
        }
        catch (IOException ioe)
        {
            System.out.println("Unable to access properties file:" + propertiesFile + " Due to:" + ioe);
        }
    }

    public Object lookup(String bindingValue) throws NamingException
    {

        // Create the initial context
        Context ctx = new InitialContext(_properties);

        // Perform the binds
        _object = ctx.lookup(bindingValue);

        // Close the context when we're done
        ctx.close();

        return getObject();
    }

    public Object getObject()
    {
        return _object;
    }

    private static String parse(String[] args, int index, String what)
    {
        try
        {
            return args[index];
        }
        catch (IndexOutOfBoundsException ioobe)
        {
            System.out.println("ERROR: No " + what + " specified.");
            System.out.println(USAGE);
            System.exit(1);
        }

        // The path is either return normally or exception.. which calls system exit so keep the compiler happy
        return "Never going to happen";
    }


    public static void main(String[] args) throws NamingException
    {
        boolean exit = false;

        String qpid_home = System.getProperty("QPID_HOME");

        if (qpid_home == null || qpid_home.equals(""))
        {
            System.out.println("QPID_HOME is not set");
            exit = true;
        }

        if (args.length <= 2)
        {
            System.out.println("At least a connection or connection factory must be requested to be bound.");
            exit = true;
        }
        else
        {
            if ((args.length - 1) % 2 != 0)
            {
                System.out.println("Not all values have full details");
                exit = true;
            }
        }
        if (exit)
        {
            System.out.println(USAGE);
            System.exit(1);
        }

        if (qpid_home.charAt(qpid_home.length() - 1) != '/')

        {
            qpid_home += "/";
        }

        for (int index = 1; index < args.length; index ++)
        {
            String obj = args[index];


            if (obj.equals("-b"))
            {
                String binding = parse(args, ++index, "binding");

                if (binding == null)
                {
                    System.out.println("Binding not specified.");
                    System.exit(1);
                }
                else
                {
                    System.out.print("Looking up:" + binding);
                    try
                    {
                        Lookup l = new Lookup(args[0], binding);

                        Object object = l.getObject();

                        if (object instanceof Connection)
                        {
                            try
                            {
                                ((Connection) object).close();
                            }
                            catch (JMSException jmse)
                            {
                                ;
                            }
                        }
                    }
                    catch (NamingException nabe)
                    {
                        System.out.println("Problem unbinding " + binding + " continuing with other values.");
                    }
                }
            }// if -b
            else
            {
                System.out.println("Continuing with other bindings option not known:" + obj);
            }
        }//for
    }//main
}//class
