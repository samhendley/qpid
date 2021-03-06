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

import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;

/**
 * Implementation of VeryfyListener for numeric values
 * @author Bhupendra Bhardwaj
 */
public class NumberVerifyListener implements VerifyListener
{
    public void verifyText(VerifyEvent event)
    {
        String string = event.text;
        char [] chars = new char [string.length ()];
        string.getChars (0, chars.length, chars, 0);
        for (int i=0; i<chars.length; i++)
        {
            if (!('0' <= chars [i] && chars [i] <= '9'))
            {
                event.doit = false;
                return;
            }
        }
    }
}
