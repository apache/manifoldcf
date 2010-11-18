/* $Id: Base64.java 988245 2010-08-23 18:39:35Z kwright $ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.manifoldcf.core.common;

import org.apache.manifoldcf.core.interfaces.*;
import java.io.*;

/** Class to manage straight and stream conversions to and from base64 encoding.
*/
public class Base64
{
  public static final String _rcsid = "@(#)$Id: Base64.java 988245 2010-08-23 18:39:35Z kwright $";

  private static final char[] base64CharacterArray =
  {
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
      'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
      'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd',
      'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
      'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
      'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7',
      '8', '9', '+', '/'
  };

  private static final byte[] mapArray;
  static
  {
    mapArray = new byte[128];

    // Initialize the array to 127's
    int i = 0;
    while (i < mapArray.length)
    {
      mapArray[i++] = Byte.MAX_VALUE;
    }

    // Now, build the reverse map
    i = 0;
    while (i < base64CharacterArray.length)
    {
      mapArray[base64CharacterArray[i]] = (byte)i;
      i++;
    }
  }

  private static final char base64PadCharacter = '=';

  /** This is a character buffer which is needed during the decoding process. */
  protected char[] characterBuffer = new char[4];
  /** This is a byte buffer which is needed during the encoding process. */
  protected byte[] byteBuffer = new byte[3];

  /** Construct the encoder/decoder.
  */
  public Base64()
  {
  }

  /** Decode the next base64 character.  Reads a single encoded word from the input stream,
  * and writes the decoded word to the output.
  *@param inputBuffer is the character input stream.
  *@param outputBuffer is the binary output stream.
  *@return false if end-of-stream encountered, true otherwise
  */
  public boolean decodeNextWord(Reader inputBuffer, OutputStream outputBuffer)
    throws ManifoldCFException
  {
    try
    {
      // First, fill character buffer accordingly
      int bufferIndex = 0;
      while (bufferIndex < characterBuffer.length)
      {
        int character = inputBuffer.read();
        if (character == -1)
        {
          if (bufferIndex != 0)
            throw new ManifoldCFException("Unexpected end of base64 input");
          return false;
        }
        char ch = (char)character;
        if (ch == base64PadCharacter || ch < mapArray.length && mapArray[ch] != Byte.MAX_VALUE)
          characterBuffer[bufferIndex++] = ch;

        // else
        //      throw new ManifoldCFException("Illegal Base64 character: '"+ch+"'");
      }

      // We have the data; do the conversion.

      int outlen = 3;
      if (characterBuffer[3] == base64PadCharacter)  outlen = 2;
      if (characterBuffer[2] == base64PadCharacter)  outlen = 1;
        int b0 = mapArray[characterBuffer[0]];
      int b1 = mapArray[characterBuffer[1]];
      int b2 = mapArray[characterBuffer[2]];
      int b3 = mapArray[characterBuffer[3]];
      switch (outlen)
      {
      case 1:
        outputBuffer.write((byte)(b0 << 2 & 0xfc | b1 >> 4 & 0x3));
        break;
      case 2:
        outputBuffer.write((byte)(b0 << 2 & 0xfc | b1 >> 4 & 0x3));
        outputBuffer.write((byte)(b1 << 4 & 0xf0 | b2 >> 2 & 0xf));
        break;
      case 3:
        outputBuffer.write((byte)(b0 << 2 & 0xfc | b1 >> 4 & 0x3));
        outputBuffer.write((byte)(b1 << 4 & 0xf0 | b2 >> 2 & 0xf));
        outputBuffer.write((byte)(b2 << 6 & 0xc0 | b3 & 0x3f));
        break;
      default:
        throw new RuntimeException("Should never occur");
      }
      return true;
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO error converting from base 64",e);
    }
  }

  /** Decode an entire stream.
  *@param inputBuffer is the character input stream.
  *@param outputBuffer is the binary output stream.
  */
  public void decodeStream(Reader inputBuffer, OutputStream outputBuffer)
    throws ManifoldCFException
  {
    // Just loop until done.
    // It may be efficient to replace this with a "bulk" method, but probably not much.
    while (decodeNextWord(inputBuffer,outputBuffer))
    {
    }
  }

  /** Decode a string into a byte array.
  *@param inputString is the string.
  *@return a corresponding byte array.
  */
  public byte[] decodeString(String inputString)
    throws ManifoldCFException
  {
    try
    {
      // Calculate the maximum size of the output array, and allocate it.  We'll copy it to the final
      // place at the end.
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream((inputString.length()>>2)*3+3);
      try
      {
        // Create an input stream from the string
        Reader reader = new StringReader(inputString);
        try
        {
          decodeStream(reader,outputStream);
          outputStream.flush();
          return outputStream.toByteArray();
        }
        finally
        {
          reader.close();
        }
      }
      finally
      {
        outputStream.close();
      }
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("Error streaming through base64 decoder",e);
    }
  }

  /** Encode a single word.
  *@param inputStream is the input binary data.
  *@param outputWriter is the character output stream.
  *@return false if end-of-stream encountered, true otherwise.
  */
  public boolean encodeNextWord(InputStream inputStream, Writer outputWriter)
    throws ManifoldCFException
  {
    try
    {
      // Try to read up to 3 bytes from the input stream
      int actualLength = inputStream.read(byteBuffer);
      if (actualLength == -1)
        return false;
      int i;
      switch (actualLength)
      {
      case 0:
        throw new ManifoldCFException("Read 0 bytes!");
      case 1:
        i = byteBuffer[0]&0xff;
        outputWriter.write(base64CharacterArray[i>>2]);
        outputWriter.write(base64CharacterArray[(i<<4)&0x3f]);
        outputWriter.write(base64PadCharacter);
        outputWriter.write(base64PadCharacter);
        break;

      case 2:
        i = ((byteBuffer[0]&0xff)<<8)+(byteBuffer[1]&0xff);
        outputWriter.write(base64CharacterArray[i>>10]);
        outputWriter.write(base64CharacterArray[(i>>4)&0x3f]);
        outputWriter.write(base64CharacterArray[(i<<2)&0x3f]);
        outputWriter.write(base64PadCharacter);
        break;

      case 3:
        i = ((byteBuffer[0]&0xff)<<16)
        +((byteBuffer[1]&0xff)<<8)
        +(byteBuffer[2]&0xff);
        outputWriter.write(base64CharacterArray[i>>18]);
        outputWriter.write(base64CharacterArray[(i>>12)&0x3f]);
        outputWriter.write(base64CharacterArray[(i>>6)&0x3f]);
        outputWriter.write(base64CharacterArray[i&0x3f]);
        break;

      default:
        throw new RuntimeException("Should never get here");
      }
      return true;
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO error encoding in base64",e);
    }
  }

  /** Encode a full stream, to the end.
  *@param inputStream is the input stream.
  *@param outputWriter is the output writer.
  */
  public void encodeStream(InputStream inputStream, Writer outputWriter)
    throws ManifoldCFException
  {
    while (encodeNextWord(inputStream,outputWriter))
    {
    }
  }

  /** Encode a byte array to a string.
  *@param inputByteArray is the byte array.
  *@return the encoded string.
  */
  public String encodeByteArray(byte[] inputByteArray)
    throws ManifoldCFException
  {
    try
    {
      Writer writer = new StringWriter((inputByteArray.length * 4) / 3 + 4);
      try
      {
        InputStream is = new ByteArrayInputStream(inputByteArray);
        try
        {
          encodeStream(is,writer);
          writer.flush();
          return writer.toString();
        }
        finally
        {
          is.close();
        }
      }
      finally
      {
        writer.close();
      }
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("Error streaming through base64 encoder",e);
    }
  }


}
