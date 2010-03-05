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

import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * This output stream works in conjunction with the WrappedInputStream
 * to introduce a protocol for sending arbitrary length data in a
 * uniform way. This output stream allows variable length data to be
 * inserted into an existing output stream so that it can be read by
 * an input stream without reading too many bytes (in case of buffering
 * by the input stream).
 * <p>
 * This output stream is used like any normal output stream. The protocol
 * is introduced by the WrappedOutputStream and does not need to be known
 * by the user of this class. However, for those that are interested, the
 * method is described below.
 * <p>
 * The output stream writes the requested bytes as packets of binary
 * information. The packet consists of a header and payload. The header
 * is two bytes of a single unsigned short (written in network order) 
 * that specifies the length of bytes in the payload. A header value of
 * 0 indicates that the stream is "closed".
 * <p>
 * <strong>Note:</strong> For this wrapped output stream to be used,
 * the application <strong>must</strong> call <code>close()</code>
 * to end the output.
 *
 * @see WrappedInputStream
 *
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public class WrappedOutputStream
    extends FilterOutputStream {

    //
    // Constants
    //

    /** Default buffer size (1024). */
    public static final int DEFAULT_BUFFER_SIZE = 1024;

    //
    // Data
    //

    /** Buffer. */
    protected byte[] fBuffer;

    /** Buffer position. */
    protected int fPosition;

    /** 
     * Data output stream. This stream is used to output the block sizes
     * into the data stream that are read by the WrappedInputStream.
     * <p>
     * <strong>Note:</strong> The data output stream is only used for
     * writing the byte count for performance reasons. We avoid the
     * method indirection for writing the byte data.
     */
    protected DataOutputStream fDataOutputStream;

    //
    // Constructors
    //

    /** Constructs a wrapper for the given output stream. */
    public WrappedOutputStream(OutputStream stream) {
        this(stream, DEFAULT_BUFFER_SIZE);
    } // <init>(OutputStream)

    /** 
     * Constructs a wrapper for the given output stream with the
     * given buffer size.
     */
    public WrappedOutputStream(OutputStream stream, int bufferSize) {
        super(stream);
        fBuffer = new byte[bufferSize];
        fDataOutputStream = new DataOutputStream(stream);
    } // <init>(OutputStream)

    //
    // OutputStream methods
    //

    /** 
     * Writes a single byte to the output. 
     * <p>
     * <strong>Note:</strong> Single bytes written to the output stream
     * will be buffered
     */
    public void write(int b) throws IOException {
        fBuffer[fPosition++] = (byte)b;
        if (fPosition == fBuffer.length) {
            fPosition = 0;
            fDataOutputStream.writeInt(fBuffer.length);
            super.out.write(fBuffer, 0, fBuffer.length);
        }
    } // write(int)

    /** Writes an array of bytes to the output. */
    public void write(byte[] b, int offset, int length) 
        throws IOException {

        // flush existing buffer
        if (fPosition > 0) {
            flush0();
        }

        // write header followed by actual bytes
        fDataOutputStream.writeInt(length);
        super.out.write(b, offset, length);

    } // write(byte[])

    /** 
     * Flushes the output buffer, writing all bytes currently in
     * the buffer to the output.
     */
    public void flush() throws IOException {
        flush0();
        super.out.flush();
    } // flush()

    /** 
     * Closes the output stream. This method <strong>must</strong> be
     * called when done writing all data to the output stream.
     * <p>
     * <strong>Note:</strong> This method does <em>not</em> close the
     * actual output stream, only makes the input stream see the stream
     * closed. Do not write bytes after closing the output stream.
     */
    public void close() throws IOException {
        flush0();
        fDataOutputStream.writeInt(0);
        super.out.flush();
    } // close()

    //
    // Protected methods
    //

    /** 
     * Flushes the output buffer, writing all bytes currently in
     * the buffer to the output. This method does not call the
     * flush() method of the output stream; it merely writes the
     * remaining bytes in the buffer.
     */
    public void flush0() throws IOException {
        int length = fPosition;
        fPosition = 0;
        if (length > 0) {
            fDataOutputStream.writeInt(length);
            super.out.write(fBuffer, 0, length);
        }
    } // flush0()

} // class WrappedOutputStream
