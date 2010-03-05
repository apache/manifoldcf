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

package socket;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

import socket.io.WrappedInputStream;
import socket.io.WrappedOutputStream;

import org.apache.xerces.parsers.SAXParser;

import org.xml.sax.AttributeList;
import org.xml.sax.DocumentHandler;
import org.xml.sax.HandlerBase;
import org.xml.sax.InputSource;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * This sample provides a solution to the problem of 1) sending multiple
 * XML documents over a single socket connection or 2) sending other types
 * of data after the XML document without closing the socket connection.
 * <p>
 * The first situation is a problem because the XML specification does
 * not allow a document to contain multiple root elements. Therefore a
 * document stream must end (or at least appear to end) for the XML
 * parser to accept it as the end of the document.
 * <p>
 * The second situation is a problem because the XML parser buffers the
 * input stream in specified block sizes for performance reasons. This
 * could cause the parser to accidentally read additional bytes of data
 * beyond the end of the document. This actually relates to the first
 * problem if the documents are encoding in two different international
 * encodings.
 * <p>
 * The solution that this sample introduces wraps both the input and
 * output stream on both ends of the socket. The stream wrappers 
 * introduce a protocol that allows arbitrary length data to be sent
 * as separate, localized input streams. While the socket stream
 * remains open, a separate input stream is created to "wrap" an
 * incoming document and make it appear as if it were a standalone
 * input stream.
 * <p>
 * To use this sample, enter any number of filenames of XML documents
 * as parameters to the program. For example:
 * <pre>
 * java socket.KeepSocketOpen doc1.xml doc2.xml doc3.xml
 * </pre>
 * <p>
 * This program will create a server and client thread that communicate
 * on a specified port number on the "localhost" address. When the client
 * connects to the server, the server sends each XML document specified
 * on the command line to the client in sequence, wrapping each document
 * in a WrappedOutputStream. The client uses a WrappedInputStream to
 * read the data and pass it to the parser.
 * <p>
 * <strong>Note:</strong> Do not send any XML documents with associated
 * grammars to the client. In other words, don't send any documents
 * that contain a DOCTYPE line that references an external DTD because
 * the client will not be able to resolve the location of the DTD and 
 * an error will be issued by the client.
 *
 * @see socket.io.WrappedInputStream
 * @see socket.io.WrappedOutputStream
 *
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public class KeepSocketOpen {

    //
    // MAIN
    //

    /** Main program entry. */
    public static void main(String[] argv) throws Exception {

        // constants
        final int port = 6789;

        // check args
        if (argv.length == 0) {
            System.out.println("usage: java socket.KeepSocketOpen file(s)");
            System.exit(1);
        }

        // create server and client
        Server server = new Server(port, argv);
        Client client = new Client("localhost", port);

        // start it running
        new Thread(server).start();
        new Thread(client).start();

    } // main(String[])

    //
    // Classes
    //

    /** 
     * Server. 
     *
     * @author Andy Clark, IBM
     */
    public static final class Server 
        extends ServerSocket 
        implements Runnable {

        //
        // Data
        //

        /** Files to send. */
        private String[] fFilenames;

        /** Verbose mode. */
        private boolean fVerbose;

        /** Buffer. */
        private byte[] fBuffer;

        //
        // Constructors
        //

        /** 
         * Constructs a server on the specified port and with the given
         * file list in terse mode. 
         */
        public Server(int port, String[] filenames) throws IOException {
            this(port, filenames, false);
        }

        /** 
         * Constructs a server on the specified port and with the given
         * file list and verbosity.
         */
        public Server(int port, String[] filenames, boolean verbose) 
            throws IOException {
            super(port);
            System.out.println("Server: Created.");
            fFilenames = filenames;
            fVerbose = verbose;
            //fBuffer = new byte[1024];
            fBuffer = new byte[4096<<2];
        } // <init>(int,String[])

        //
        // Runnable methods
        //

        /** Runs the server. */
        public void run() {

            System.out.println("Server: Running.");
            final Random random = new Random(System.currentTimeMillis());
            try {

                // accept connection
                if (fVerbose) System.out.println("Server: Waiting for Client connection...");
                final Socket clientSocket = accept();
                final OutputStream clientStream = clientSocket.getOutputStream();
                System.out.println("Server: Client connected.");

                // send files, one at a time
                for (int i = 0; i < fFilenames.length; i++) {

                    // open file
                    String filename = fFilenames[i];
                    System.out.println("Server: Opening file \""+filename+'"');
                    FileInputStream fileIn = new FileInputStream(filename);
                    
                    // wrap stream
                    if (fVerbose) System.out.println("Server: Wrapping output stream.");
                    WrappedOutputStream wrappedOut = new WrappedOutputStream(clientStream);
                    
                    // read file, writing to output
                    int total = 0;
                    while (true) {

                        // read random amount
                        //int length = (Math.abs(random.nextInt()) % fBuffer.length) + 1;
                        int length = fBuffer.length;
                        if (fVerbose) System.out.println("Server: Attempting to read "+length+" byte(s).");
                        int count = fileIn.read(fBuffer, 0, length);
                        if (count == -1) {
                            if (fVerbose) System.out.println("Server: EOF.");
                            break;
                        }
                        if (fVerbose) System.out.println("Server: Writing "+count+" byte(s) to wrapped output stream.");
                        wrappedOut.write(fBuffer, 0, count);
                        total += count;
                    }
                    System.out.println("Server: Wrote "+total+" byte(s) total.");

                    // close stream
                    if (fVerbose) System.out.println("Server: Closing output stream.");
                    wrappedOut.close();
                    
                    // close file
                    if (fVerbose) System.out.println("Server: Closing file.");
                    fileIn.close();
                }

                // close connection to client
                if (fVerbose) System.out.println("Server: Closing socket.");
                clientSocket.close();

            }
            catch (IOException e) {
                System.out.println("Server ERROR: "+e.getMessage());
            }
            System.out.println("Server: Exiting.");

        } // run()

    } // class Server

    /**
     * Client.
     *
     * @author Andy Clark, IBM
     */
    public static final class Client
        extends HandlerBase
        implements Runnable {

        //
        // Data
        //

        /** Socket. */
        private Socket fServerSocket;

        /** Wrapped input stream. */
        private WrappedInputStream fWrappedInputStream;

        /** Verbose mode. */
        private boolean fVerbose;

        /** Buffer. */
        private byte[] fBuffer;

        /** Parser. */
        private SAXParser fParser;

        // parse data

        /** Number of elements. */
        private int fElementCount;

        /** Number of attributes. */
        private int fAttributeCount;

        /** Number of ignorable whitespace. */
        private int fIgnorableWhitespaceCount;

        /** Number of characters. */
        private int fCharactersCount;

        /** Time at start of parse. */
        private long fTimeBefore;

        //
        // Constructors
        //

        /** 
         * Constructs a Client that connects to the given port in terse
         * output mode. 
         */
        public Client(String address, int port) throws IOException {
            this(address, port, false);
            fParser = new SAXParser();
            fParser.setDocumentHandler(this);
            fParser.setErrorHandler(this);
        }

        /** 
         * Constructs a Client that connects to the given address:port and
         * with the specified verbosity. 
         */
        public Client(String address, int port, boolean verbose) 
            throws IOException {
            System.out.println("Client: Created.");
            fServerSocket = new Socket(address, port);
            fVerbose = verbose;
            fBuffer = new byte[1024];
        } // <init>(String,int)

        //
        // Runnable methods
        //

        /** Runs the client. */
        public void run() {

            System.out.println("Client: Running.");
            try {
                // get input stream
                final InputStream serverStream = fServerSocket.getInputStream();

                // read files from server
                while (!Thread.interrupted()) {
                    // wrap input stream
                    if (fVerbose) System.out.println("Client: Wrapping input stream.");
                    fWrappedInputStream = new WrappedInputStream(serverStream);
                    InputStream in = new InputStreamReporter(fWrappedInputStream);

                    // parse file
                    if (fVerbose) System.out.println("Client: Parsing XML document.");
                    InputSource source = new InputSource(in);
                    fParser.parse(source);
                    fWrappedInputStream = null;

                    // close stream
                    if (fVerbose) System.out.println("Client: Closing input stream.");
                    in.close();

                }

                // close socket
                if (fVerbose) System.out.println("Client: Closing socket.");
                fServerSocket.close();

            }
            catch (EOFException e) {
                // server closed connection; ignore
            }
            catch (Exception e) {
                System.out.println("Client ERROR: "+e.getMessage());
            }
            System.out.println("Client: Exiting.");

        } // run()

        //
        // DocumentHandler methods
        //

        /** Start document. */
        public void startDocument() {
            fElementCount = 0;
            fAttributeCount = 0;
            fIgnorableWhitespaceCount = 0;
            fCharactersCount = 0;
            fTimeBefore = System.currentTimeMillis();
        } // startDocument()

        /** Start element. */
        public void startElement(String name, AttributeList attrs) {
            fElementCount++;
            fAttributeCount += attrs != null ? attrs.getLength() : 0;
        } // startElement(String,AttributeList)

        /** Ignorable whitespace. */
        public void ignorableWhitespace(char[] ch, int offset, int length) {
            fIgnorableWhitespaceCount += length;
        } // ignorableWhitespace(char[],int,int)

        /** Characters. */
        public void characters(char[] ch, int offset, int length) {
            fCharactersCount += length;
        } // characters(char[],int,int)

        /** End document. */
        public void endDocument() {
            long timeAfter = System.currentTimeMillis();
            System.out.print("Client: ");
            System.out.print(timeAfter - fTimeBefore);
            System.out.print(" ms (");
            System.out.print(fElementCount);
            System.out.print(" elems, ");
            System.out.print(fAttributeCount);
            System.out.print(" attrs, ");
            System.out.print(fIgnorableWhitespaceCount);
            System.out.print(" spaces, ");
            System.out.print(fCharactersCount);
            System.out.print(" chars)");
            System.out.println();
        } // endDocument()

        //
        // ErrorHandler methods
        //

        /** Warning. */
        public void warning(SAXParseException e) throws SAXException {
            System.out.println("Client: [warning] "+e.getMessage());
        } // warning(SAXParseException)

        /** Error. */
        public void error(SAXParseException e) throws SAXException {
            System.out.println("Client: [error] "+e.getMessage());
        } // error(SAXParseException)

        /** Fatal error. */
        public void fatalError(SAXParseException e) throws SAXException {
            System.out.println("Client: [fatal error] "+e.getMessage());
            // on fatal error, skip to end of stream and end parse
            try {
                fWrappedInputStream.close();
            }
            catch (IOException ioe) {
                // ignore
            }
            throw e;
        } // fatalError(SAXParseException)

        //
        // Classes
        //

        /**
         * This class reports the actual number of bytes read at the
         * end of "stream".
         *
         * @author Andy Clark, IBM
         */
        class InputStreamReporter
            extends FilterInputStream {

            //
            // Data
            //

            /** Total bytes read. */
            private long fTotal;

            //
            // Constructors
            //

            /** Constructs a reporter from the specified input stream. */
            public InputStreamReporter(InputStream stream) {
                super(stream);
            } // <init>(InputStream)

            //
            // InputStream methods
            //

            /** Reads a single byte. */
            public int read() throws IOException {
                int b = super.in.read();
                if (b == -1) {
                    System.out.println("Client: Read "+fTotal+" byte(s) total.");
                    return -1;
                }
                fTotal++;
                return b;
            } // read():int

            /** Reads a block of bytes. */
            public int read(byte[] b, int offset, int length) 
                throws IOException {
                int count = super.in.read(b, offset, length);
                if (count == -1) {
                    System.out.println("Client: Read "+fTotal+" byte(s) total.");
                    return -1;
                }
                fTotal += count;
                if (Client.this.fVerbose) System.out.println("Client: Actually read "+count+" byte(s).");
                return count;
            } // read(byte[],int,int):int

        } // class InputStreamReporter

    } // class Client

} // class KeepSocketOpen
