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

package socket.io;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * This input stream works in conjunction with the WrappedOutputStream
 * to introduce a protocol for reading arbitrary length data in a
 * uniform way.
 * <p>
 * <strong>Note:</strong> See the javadoc for WrappedOutputStream for
 * more information.
 *
 * @see WrappedOutputStream
 *
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public class WrappedInputStream
    extends FilterInputStream {

    //
    // Data
    //

    /** Bytes left on input stream for current packet. */
    protected int fPacketCount;

    /** 
     * Data input stream. This stream is used to input the block sizes
     * from the data stream that are written by the WrappedOutputStream.
     * <p>
     * <strong>Note:</strong> The data input stream is only used for
     * reading the byte count for performance reasons. We avoid the
     * method indirection for reading the byte data.
     */
    protected DataInputStream fDataInputStream;

    /** To mark that the stream is "closed". */
    protected boolean fClosed;

    //
    // Constructors
    //

    /** Constructs a wrapper for the given an input stream. */
    public WrappedInputStream(InputStream stream) {
        super(stream);
        fDataInputStream = new DataInputStream(stream);
    } // <init>(InputStream)

    //
    // InputStream methods
    //

    /** Reads a single byte. */
    public int read() throws IOException {

        // ignore, if already closed
        if (fClosed) {
            return -1;
        }

        // read packet header
        if (fPacketCount == 0) {
            fPacketCount = fDataInputStream.readInt() & 0x7FFFFFFF;
            if (fPacketCount == 0) {
                fClosed = true;
                return -1;
            }
        }

        // read a byte from the packet
        fPacketCount--;
        return super.in.read();

    } // read():int

    /** 
     * Reads a block of bytes and returns the total number of bytes read. 
     */
    public int read(byte[] b, int offset, int length) throws IOException {

        // ignore, if already closed
        if (fClosed) {
            return -1;
        }

        // read packet header
        if (fPacketCount == 0) {
            fPacketCount = fDataInputStream.readInt() & 0x7FFFFFFF;
            if (fPacketCount == 0) {
                fClosed = true;
                return -1;
            }
        }

        // read bytes from packet
        if (length > fPacketCount) {
            length = fPacketCount;
        }
        int count = super.in.read(b, offset, length);
        if (count == -1) {
            // NOTE: This condition should not happen. The end of 
            //       the stream should always be designated by a 
            //       byte count header of 0. -Ac
            fClosed = true;
            return -1;
        }
        fPacketCount -= count;

        // return total bytes read
        return count;

    } // read(byte[],int,int):int

    /** Skips the specified number of bytes from the input stream. */
    public long skip(long n) throws IOException {
        if (!fClosed) {
            // NOTE: This should be rewritten to be more efficient. -Ac
            for (long i = 0; i < n; i++) {
                int b = read();
                if (b == -1) {
                    return i + 1;
                }
            }
            return n;
        }
        return 0;
    } // skip(long):long

    /** 
     * Closes the input stream. This method will search for the end of
     * the wrapped input, positioning the stream at after the end packet.
     * <p>
     * <strong>Note:</strong> This method does not close the underlying
     * input stream.
     */
    public void close() throws IOException {
        if (!fClosed) {
            fClosed = true;
            do {
                super.in.skip(fPacketCount);
                fPacketCount = fDataInputStream.readInt() & 0x7FFFFFFF;
            } while (fPacketCount > 0);
        }
    } // close()

} // class WrappedInputStream
