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

package jaxp;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * <p>A sample which demonstrates usage of the JAXP 1.3 Datatype API.</p>
 * 
 * @version $Id$
 */
public class DatatypeAPIUsage {
    
    public static void main (String [] args) {
        try {
            DatatypeFactory df = DatatypeFactory.newInstance();
            // my work number in milliseconds:
            Duration myPhone = df.newDuration(9054133519l);
            Duration myLife = df.newDuration(true, 29, 2, 15, 13, 45, 0);
            int compareVal = myPhone.compare(myLife);
            switch (compareVal) {
                case DatatypeConstants.LESSER:
                    System.out.println("There are fewer milliseconds in my phone number than my lifespan.");
                    break;
                case DatatypeConstants.EQUAL:
                    System.out.println("The same number of milliseconds are in my phone number and my lifespan.");
                    break;
                case DatatypeConstants.GREATER:
                    System.out.println("There are more milliseconds in my phone number than my lifespan.");
                    break;
                case DatatypeConstants.INDETERMINATE:
                    System.out.println("The comparison could not be carried out.");
            }
            
            // create a yearMonthDuration
            Duration ymDuration = df.newDurationYearMonth("P12Y10M");
            System.out.println("P12Y10M is of type: " + ymDuration.getXMLSchemaType());
            
            // create a dayTimeDuration (really this time)
            Duration dtDuration = df.newDurationDayTime("P10DT10H12M0S");
            System.out.println("P10DT10H12M0S is of type: " + dtDuration.getXMLSchemaType());
            
            // try to fool the factory!
            try {
                ymDuration = df.newDurationYearMonth("P12Y10M1D");
            }
            catch(IllegalArgumentException e) {
                System.out.println("'duration': P12Y10M1D is not 'yearMonthDuration'!!!");
            }
            
            XMLGregorianCalendar xgc = df.newXMLGregorianCalendar();
            xgc.setYear(1975);
            xgc.setMonth(DatatypeConstants.AUGUST);
            xgc.setDay(11);
            xgc.setHour(6);
            xgc.setMinute(44);
            xgc.setSecond(0);
            xgc.setMillisecond(0);
            xgc.setTimezone(5);
            xgc.add(myPhone);
            System.out.println("The approximate end of the number of milliseconds in my phone number was " + xgc);
            
            //adding a duration to XMLGregorianCalendar
            xgc.add(myLife);
            System.out.println("Adding the duration myLife to the above calendar:" + xgc);
            
            // create a new XMLGregorianCalendar using the string format of xgc.
            XMLGregorianCalendar xgcCopy = df.newXMLGregorianCalendar(xgc.toXMLFormat());
            
            // should be equal-if not what happened!!
            if (xgcCopy.compare(xgc) != DatatypeConstants.EQUAL) {
                System.out.println("oooops!");
            }
            else {
                System.out.println("Very good: " + xgc +  " is equal to " + xgcCopy);
            }
        }
        catch (DatatypeConfigurationException dce) {
            System.err.println("error: Datatype error occurred - " + dce.getMessage());
            dce.printStackTrace(System.err);
        }
    } // main(String[])
    
} // DatatypeAPIUsage
