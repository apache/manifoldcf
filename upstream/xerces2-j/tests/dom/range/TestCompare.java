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
package dom.range;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.xerces.dom.DocumentImpl;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.w3c.dom.ranges.Range;

/**
 * This class is used to validate that the implementation of DOM Ranges
 * is functionally correct.  We include in this test suite examples 
 * from the DOM Spec.
 *
 * @author Lynn Monson
 * 
 * @version $Id$
 */
public class TestCompare extends TestCase 
{
    /**
     * Creates an instance of the test
     */
    public TestCompare(String name) {
            super(name);
    }

    /**
     * Builds a set of ranges that correspond to the range example from
     * section 2.1 of the DOM range spec.  These ranges are based on a 
     * document that looks like:
     * 
     *     <BODY><H1>Title</H1><P>Blah xyz.</P></BODY>
     * 
     * The ranges are as follows:
     *  Range   Start-node  Start-Offset        End-node    End-Offset
     *   0      "Title"     2                   "Blah.."    2
     *   1      BODY        1                   BODY        2
     *   2      P           0                   P           1
     *   3      "Blah.."    0                   "Blah.."    9
     * 
     * These ranges are in sorted order based on the boundary point
     * of the start of each range, in document order.  
     * 
     * The ending points of the ranges are not in any particular order.
     * These ranges cover all four boundary tests as enumerated in
     * the DOM range specification.
     */
    private Range[] buildRanges()
    {
        DocumentImpl doc=new org.apache.xerces.dom.DocumentImpl();

        Element body = doc.createElement("BODY");
        doc.appendChild(body);
        Element h1 = doc.createElement("H1");
        body.appendChild(h1);
        Text title = doc.createTextNode("Title");
        h1.appendChild(title);
        Element p = doc.createElement("P");
        body.appendChild(p);
        Text blah = doc.createTextNode("Blah xyz.");
        p.appendChild(blah);

        // We are creating the four ranges specified in the DOM example.
        Range[] ranges = new Range[4];

        ranges[0] = doc.createRange();
        ranges[0].setStart( title, 2 );
        ranges[0].setEnd( blah, 2 );

        ranges[1] = doc.createRange();
        ranges[1].setStart( body, 1 );
        ranges[1].setEnd( body, 2 );

        ranges[2] = doc.createRange();
        ranges[2].setStart( p, 0 );
        ranges[2].setEnd( p, 1 );

        ranges[3] = doc.createRange();
        ranges[3].setStart( blah, 0 );
        ranges[3].setEnd( blah, 9 );

        return ranges;
    }

    /**
     * This table is the set of compareBoundaryPoints results you can
     * expect to see when comparing the start-to-start points of ranges found in 
     * the DOM Spec section 2.1.  These ranges are built by the above
     * buildRanges() method. 
     */
    private final int[][] results_START_TO_START = 
    {
        { 0, -1, -1, -1 },  // range[0].compareBoundaryPoints( range[x] )
        { 1, 0, -1, -1 },   // range[1].compareBoundaryPoints( range[x] )
        { 1, 1, 0, -1 },    // range[2].compareBoundaryPoints( range[x] )
        { 1, 1, 1, 0 },     // range[3].compareBoundaryPoints( range[x] )
    };

    /**
     * This table is the set of compareBoundaryPoints results you can
     * expect to see when comparing the start-to-end points of ranges found in 
     * the DOM Spec section 2.1.  These ranges are built by the above
     * buildRanges() method. 
     */
    private final int[][] results_START_TO_END = 
    {
        { 1, 1, 1, 1 },  // range[0].compareBoundaryPoints( range[x] ) 
        { 1, 1, 1, 1 },  // range[1].compareBoundaryPoints( range[x] ) 
        { 1, 1, 1, 1 },  // range[2].compareBoundaryPoints( range[x] ) 
        { 1, 1, 1, 1 },  // range[3].compareBoundaryPoints( range[x] ) 
    };

    /**
     * This table is the set of compareBoundaryPoints results you can
     * expect to see when comparing the end-to-start points of ranges found in 
     * the DOM Spec section 2.1.  These ranges are built by the above
     * buildRanges() method. 
     */
    private final int[][] results_END_TO_START = 
    {
        { -1, -1, -1, -1 },    // range[0].compareBoundaryPoints( range[x] ) 
        { -1, -1, -1, -1 },    // range[0].compareBoundaryPoints( range[x] ) 
        { -1, -1, -1, -1 },    // range[0].compareBoundaryPoints( range[x] ) 
        { -1, -1, -1, -1 },    // range[0].compareBoundaryPoints( range[x] ) 
    };

    /**
     * This table is the set of compareBoundaryPoints results you can
     * expect to see when comparing the end-to-end points of ranges found in 
     * the DOM Spec section 2.1.  These ranges are built by the above
     * buildRanges() method. 
     */
    private final int[][] results_END_TO_END = 
    {
        { 0, -1, -1, -1 },       // range[0].compareBoundaryPoints( range[x] ) 
        { 1, 0, 1, 1 },          // range[1].compareBoundaryPoints( range[x] ) 
        { 1, -1, 0, 1 },         // range[2].compareBoundaryPoints( range[x] ) 
        { 1, -1, -1, 0 },        // range[3].compareBoundaryPoints( range[x] ) 
    };

    /**
     * Utility method used to compare the Ranges from the 
     * buildRanges() method.  The caller specifies how the ranges
     * should be compared and what the results should be.
     */
    private void doTestCompare( short how, int[][] results )
    {
        // get the sample ranges
        Range[] ranges = buildRanges();

        // Compare every pair of ranges.
        for( int i=0; i<ranges.length; ++i )
        {
            for( int j=0; j<ranges.length; ++j )
            {
                int result = ranges[i].compareBoundaryPoints( how, ranges[j] );
                assertTrue( "Compare returned the wrong value i="+i+" j="+j + " result="+result, result == results[i][j] );
            }
        }
    }

    /**
     * Using all of the sample ranges from section 2.1 of the DOM
     * specification, compare each starting point to every other 
     * starting point.
     */
    public void testCompareStartToStart()
    {
        doTestCompare( Range.START_TO_START, results_START_TO_START );
    }

    /**
     * Using all of the sample ranges from section 2.1 of the DOM
     * specification, compare each starting point to every other 
     * ending point.
     */
    public void testCompareStartToEnd()
    {
        doTestCompare( Range.START_TO_END, results_START_TO_END );
    }

    /**
     * Using all of the sample ranges from section 2.1 of the DOM
     * specification, compare each ending point to every other 
     * starting point.
     */
    public void testCompareEndToStart()
    {
        doTestCompare( Range.END_TO_START, results_END_TO_START );
    }
    
    /**
     * Using all of the sample ranges from section 2.1 of the DOM
     * specification, compare each ending point to every other 
     * ending point.
     */
    public void testCompareEndToEnd()
    {
        doTestCompare( Range.END_TO_END, results_END_TO_END );
    }

    /**
     * Returns the set of all tests in this class
     */
    public static junit.framework.Test suite() {
        return new TestSuite( TestCompare.class );
    }

    /**
     * Utility for invoking the class from the command line.
     */
    public static void main (String[] args) {
            junit.textui.TestRunner.run (suite());
    }

}

