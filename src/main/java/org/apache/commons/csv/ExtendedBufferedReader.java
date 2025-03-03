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

package org.apache.commons.csv;

import static org.apache.commons.csv.Constants.CR;
import static org.apache.commons.csv.Constants.END_OF_STREAM;
import static org.apache.commons.csv.Constants.LF;
import static org.apache.commons.csv.Constants.UNDEFINED;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * A special buffered reader which supports sophisticated read access.
 * <p>
 * In particular the reader supports a look-ahead option, which allows you to see the next char returned by
 * {@link #read()}. This reader also tracks how many characters have been read with {@link #getPosition()}.
 * </p>
 */
final class ExtendedBufferedReader extends BufferedReader {

    /** The last char returned */
    private int lastChar = UNDEFINED;

    /** The count of EOLs (CR/LF/CRLF) seen so far */
    private long eolCounter;

    /** The position, which is the number of characters read so far */
    private long position;

    private boolean closed;

    /**
     * Constructs a new instance using the default buffer size.
     */
    ExtendedBufferedReader(final Reader reader) {
        super(reader);
    }

    /**
     * Closes the stream.
     *
     * @throws IOException
     *             If an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        // Set ivars before calling super close() in case close() throws an IOException.
        closed = true;
        lastChar = END_OF_STREAM;
        super.close();
    }

    /**
     * Returns the current line number
     *
     * @return the current line number
     */
    long getCurrentLineNumber() {
        // Check if we are at EOL or EOF or just starting
        if (lastChar == CR || lastChar == LF || lastChar == UNDEFINED || lastChar == END_OF_STREAM) {
            return eolCounter; // counter is accurate
        }
        return eolCounter + 1; // Allow for counter being incremented only at EOL
    }

    /**
     * Returns the last character that was read as an integer (0 to 65535). This will be the last character returned by
     * any of the read methods. This will not include a character read using the {@link #lookAhead()} method. If no
     * character has been read then this will return {@link Constants#UNDEFINED}. If the end of the stream was reached
     * on the last read then this will return {@link Constants#END_OF_STREAM}.
     *
     * @return the last character that was read
     */
    int getLastChar() {
        return lastChar;
    }

    /**
     * Gets the character position in the reader.
     *
     * @return the current position in the reader (counting characters, not bytes since this is a Reader)
     */
    long getPosition() {
        return this.position;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns the next character in the current reader without consuming it. So the next call to {@link #read()} will
     * still return this value. Does not affect the line number or the last character.
     *
     * @return the next character
     *
     * @throws IOException
     *             If an I/O error occurs
     */
    int lookAhead() throws IOException {
        super.mark(1);
        final int c = super.read();
        super.reset();

        return c;
    }

    /**
     * Populates the buffer with the next {@code buf.length} characters in the
     * current reader without consuming them. The next call to {@link #read()} will
     * still return the next value. This doesn't affect the line number or the last
     * character.
     *
     * @param buf the buffer to fill for the look ahead.
     * @return the buffer itself
     * @throws IOException If an I/O error occurs
     */
    char[] lookAhead(final char[] buf) throws IOException {
        final int n = buf.length;
        super.mark(n);
        super.read(buf, 0, n);
        super.reset();

        return buf;
    }

    /**
     * Returns the next n characters in the current reader without consuming them. The next call to {@link #read()} will still return the next value. This
     * doesn't affect line number or last character.
     *
     * @param n the number characters look ahead.
     * @return the next n characters.
     * @throws IOException If an I/O error occurs
     */
    char[] lookAhead(final int n) throws IOException {
        final char[] buf = new char[n];
        return lookAhead(buf);
    }

    @Override
    public int read() throws IOException {
        final int current = super.read();
        if (current == CR || current == LF && lastChar != CR ||
            current == END_OF_STREAM && lastChar != CR && lastChar != LF && lastChar != END_OF_STREAM) {
            eolCounter++;
        }
        lastChar = current;
        position++;
        return lastChar;
    }

    @Override
    public int read(final char[] buf, final int offset, final int length) throws IOException {
        if (length == 0) {
            return 0;
        }

        final int len = super.read(buf, offset, length);

        if (len > 0) {

            for (int i = offset; i < offset + len; i++) {
                final char ch = buf[i];

                if (isEndOfLine(ch, i , offset, buf, lastChar)) {
                  eolCounter++;
                }

            }

            lastChar = buf[offset + len - 1];

        } else if (len == -1) {
            lastChar = END_OF_STREAM;
        }

        position += len;
        return len;
    }

    private boolean isEndOfLine(final char ch, int i, int offset, final char[] buf, int lastChar) {
        if (ch == LF) {
            if (CR != (i > offset ? buf[i - 1] : lastChar)) {
                return true;
            }
        } else if (ch == CR) {
            return true;
        }
        return false;
    }

    /**
     * Gets the next line, dropping the line terminator(s). This method should only be called when processing a
     * comment, otherwise, information can be lost.
     * <p>
     * Increments {@link #eolCounter} and updates {@link #position}.
     * </p>
     * <p>
     * Sets {@link #lastChar} to {@link Constants#END_OF_STREAM} at EOF, otherwise the last EOL character.
     * </p>
     *
     * @return the line that was read, or null if reached EOF.
     */
    @Override
    public String readLine() throws IOException {
        if (lookAhead() == END_OF_STREAM) {
            return null;
        }
        final StringBuilder buffer = new StringBuilder();
        while (true) {
            final int current = read();
            if (current == CR) {
                final int next = lookAhead();
                if (next == LF) {
                    read();
                }
            }
            if (current == END_OF_STREAM || current == LF || current == CR) {
                break;
            }
            buffer.append((char) current);
        }
        return buffer.toString();
    }

}
