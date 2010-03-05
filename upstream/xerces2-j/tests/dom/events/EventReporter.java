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

package dom.events;

import org.w3c.dom.Node;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.MutationEvent;

class EventReporter implements EventListener
{
    boolean silent=false; // Toggle this to mask reports you don't care about
    int count=0;
    String[] phasename={"?","CAPTURING","AT_TARGET","BUBBLING","?"};
    String[] attrChange={"?","MODIFICATION","ADDITION","REMOVAL"};

    public void on()
    {
        System.out.println();
        System.out.println("EventReporter awakened:");
        System.out.println();
        silent=false;
    }
    public void off()
    {
        System.out.println();
        System.out.println("EventReporter muted");
        System.out.println();
        silent=true;
    }
    
    public void handleEvent(Event evt)
    {
        ++count;
        if(silent)
            return;
            
        System.out.print("EVT "+count+": '"+
            evt.getType()+
            "' listener '"+((Node)evt.getCurrentTarget()).getNodeName()+
            "' target '"+((Node)evt.getTarget()).getNodeName()+
            "' while "+phasename[evt.getEventPhase()] +
            "... ");
        if(evt.getBubbles()) System.out.print("will bubble");
        if(evt.getCancelable()) System.out.print("can cancel");
        System.out.println();
        if(evt instanceof MutationEvent)
        {
            MutationEvent me=(MutationEvent)evt;
            if(me.getRelatedNode()!=null)
                System.out.println("\trelatedNode='"+me.getRelatedNode()+"'");
            if(me.getAttrName()!=null)
                System.out.println("\tattrName='"+me.getAttrName()+"'");
            if(me.getPrevValue()!=null)
                System.out.println("\tprevValue='"+me.getPrevValue()+"'");
            if(me.getNewValue()!=null)
                System.out.println("\tnewValue='"+me.getNewValue()+"'");
            if(me.getType().equals("DOMAttrModified"))
                System.out.println("\tattrChange='"+attrChange[me.getAttrChange()]+"'");
        }
    }
}
