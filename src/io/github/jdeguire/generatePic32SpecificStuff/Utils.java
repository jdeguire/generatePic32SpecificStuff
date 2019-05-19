/* Copyright (c) 2019, Jesse DeGuire
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 * 
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.github.jdeguire.generatePic32SpecificStuff;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * This is just a place to put simple utility functions that do not really fit anywhere else.
 */
public class Utils {

    /* Create a PrintWriter that will use Unix line endings ('\n') instead of the system default.  
     * The parameter is a string representing the full path to the file.  Any directories in the
     * given path that do not exist will be created.
     */
    public static PrintWriter CreateUnixPrintWriter(String filePath) 
                                throws java.io.FileNotFoundException {
        File temp = new File(filePath);
        temp.getParentFile().mkdirs();
        
        return new PrintWriter(temp) {
                        @Override
                        public void println() {
                            write('\n');
                        }
                    };
    }

    /* Write a multiline C comment using the given writer, automatically wrapping the string at 100
     * characters.  This will add 'indent' number of spaces before the comment block (max 60).  The 
     * comment will be laid out like the one containing this text.  This does not trim whitespace.
     */
    public static void WriteMultilineCComment(PrintWriter writer, int indent, String str) {
        if(indent > 60)
            indent = 60;

        char[] spaces = new char[indent];
        Arrays.fill(spaces, ' ');
        String spacesStr = new String(spaces);

        // -3 to account for comment symbols at start ("/* " or " * " or " */").
        String lines[] = CreateWrappedString(str, 100 - indent - 3).split("\n");

        if(lines.length > 0) {
            writer.println(spacesStr + "/* " + lines[0]);

            for(int i = 1; i < lines.length; ++i) {
                writer.println(spacesStr + " * " + lines[i]);
            }
        } else {
            writer.println(spacesStr + "/* ");
        }

        writer.println(spacesStr + " */");
    }

    /* Create a new string that is a wrapped version of the given string by copying the original
     * string while adding newline ('\n') characters into it where needed to wrap the string to the
     * given width.
     */
    public static String CreateWrappedString(String str, int width) {
        if(str.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(str.length());

        int lastWhiteSpace = -1;
        int index = 0;
        int count = 0;

        while(true) {
            char ch = str.charAt(index);

            // Wrap immediately if we find a line separator.
            switch(ch) {
                case '\n':
                    sb.append(str.substring(index-count, index));
                    sb.append('\n');
                    lastWhiteSpace = -1;
                    count = 0;
                    break;
                case '\r':
                    sb.append(str.substring(index-count, index));
                    sb.append('\n');
                    lastWhiteSpace = -1;
                    count = 0;

                    // See if we have a CRLF pair and move past it if so.
                    if(index < str.length()-1  &&   '\n' == str.charAt(index+1)) {
                        ++index;
                    }
                    break;
                case ' ':
                case '\t':
                    lastWhiteSpace = index;
                    // fall through
                default:
                    ++count;
                    break;
            }

            ++index;

            if(index >= str.length()) {
                // At end of string, so we're done!
                sb.append(str.substring(index-count, index));
                break;
            }

            if(count > width) {
                if(-1 == lastWhiteSpace) {
                    // Very long word, so it'll just get broken up.
                    sb.append(str.substring(index-count, index));
                    count = 0;
                } else {
                    sb.append(str.substring(index-count, lastWhiteSpace));
                    count -= lastWhiteSpace;
                    index -= lastWhiteSpace;
                    lastWhiteSpace = -1;
                }

                sb.append('\n');
            }
        }

        return sb.toString();
    }
}
