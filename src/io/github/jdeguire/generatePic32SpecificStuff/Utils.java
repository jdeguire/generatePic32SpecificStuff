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
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is just a place to put simple utility functions that do not really fit anywhere else.
 */
public class Utils {

    /* Create a PrintWriter that will use Unix line endings ('\n') instead of the system default.  
     * The parameter is a string representing the full path to the file.  Any directories in the
     * given path that do not exist will be created.
     */
    public static PrintWriter createUnixPrintWriter(String filePath) 
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
    public static void writeMultilineCComment(PrintWriter writer, int indent, String str) {
        if(indent > 60)
            indent = 60;
        else if(indent < 0)
            indent = 0;
        
        char[] spaces = new char[indent];
        Arrays.fill(spaces, ' ');
        String spacesStr = new String(spaces);

        // -3 to account for comment symbols at start ("/* " or " * " or " */").
        String lines[] = createWrappedString(str, 100 - indent - 3).split("\n");

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
// TODO:  This should probably just return an array of lines
    public static String createWrappedString(String str, int width) {
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
                    ++count;
                    break;
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
                    count = 0;
                    index = lastWhiteSpace + 1;
                    lastWhiteSpace = -1;
                }

                sb.append('\n');
            }
        }
        
        return sb.toString();
    }


    /* Find the first child of the given node that meets the criteria set by the other arguments.  
     * Set the argument to null to allow the child to always pass through the filter.  For example,
     * use
     * <code>
     * filterFirstChildNode(someNode, null, "my_attribute", "my_value");
     * </code>
     * to look for any node that has the given attribute with the given value.  The attribute value
     * is checked only if the attribute name is non-null.
     */
    public static Node filterFirstChildNode(Node node, String nodename, String attrname, String attrvalue) {
        if(null != node  &&  node.hasChildNodes()) {
            NodeList children = node.getChildNodes();

            for(int i = 0; i < children.getLength(); ++i) {
                Node child = children.item(i);

                if(null != nodename  &&  !child.getNodeName().equals(nodename)) {
                    continue;
                }

                if(null != attrname) {
                    if(!child.hasAttributes()) {
                        continue;
                    }

                    Node attributeNode = child.getAttributes().getNamedItem(attrname);

                    if(null == attributeNode || 
                       (null != attrvalue  &&  !attributeNode.getNodeValue().equals(attrvalue))) {
                        continue;
                    }
                }

                return child;
            }
        }

        return null;        
    }

    /* Find the all children of the given node that meets the criteria set by the other arguments.  
     * Set the argument to null to allow the children to always pass through the filter.  For example,
     * use
     * <code>
     * filterFirstChildNode(someNode, "my_name", "my_attribute", null);
     * </code>
     * to look for all nodes named "my_name" and with the attribute "my_attribute" regardless of the
     * the attribute's value.  The attribute value is checked only if the attribute name is non-null.
     */
    public static List<Node> filterAllChildNodes(Node node, String nodename, String attrname, String attrvalue) {
        ArrayList<Node> filteredChildren = new ArrayList<>(10);
        
        if(null != node  &&  node.hasChildNodes()) {
            NodeList children = node.getChildNodes();

            for(int i = 0; i < children.getLength(); ++i) {
                Node child = children.item(i);

                if(null != nodename  &&  !child.getNodeName().equals(nodename)) {
                    continue;
                }

                if(null != attrname) {
                    if(!child.hasAttributes()) {
                        continue;
                    }

                    Node attributeNode = child.getAttributes().getNamedItem(attrname);

                    if(null == attributeNode || 
                       (null != attrvalue  &&  !attributeNode.getNodeValue().equals(attrvalue))) {
                        continue;
                    }
                }

                filteredChildren.add(child);
            }
        }

        return filteredChildren;
    }

    /* Return given node attribute as a String or return the given fallback value if the node does
     * not have an attribute of the given name.
     */
    public static String getNodeAttribute(Node node, String attrname, String fallback) {
        Node attrNode = node.getAttributes().getNamedItem(attrname);
        String attrValue = fallback;

        if(null != attrNode)
            attrValue = attrNode.getNodeValue();

        return attrValue;
    }

    /* Like above, but attempts to parse the attribute value as an integer.  This will return the 
     * fallback value if the attribute does not exist or if the value cannot be parsed as an int.
    */
    public static int getNodeAttributeAsInt(Node node, String attrname, int fallback) {
        int result = fallback;

        try {
            String attrStr = getNodeAttribute(node, attrname, null);

            if(null != attrStr)
                result = Integer.decode(attrStr);
        } catch(NumberFormatException nfe) {
            result = fallback;
        }

        return result;
    }

    /* Like above, but attempts to parse the attribute value as a long.  This will return the 
     * fallback value if the attribute does not exist or if the value cannot be parsed as a long.
    */
    public static long getNodeAttributeAsLong(Node node, String attrname, long fallback) {
        long result = fallback;

        try {
            String attrStr = getNodeAttribute(node, attrname, null);

            if(null != attrStr)
                result = Long.decode(attrStr);
        } catch(NumberFormatException nfe) {
            result = fallback;
        }

        return result;
    }

    /* Like above, but attempts to parse the attribute value as a boolean.  This will return the 
     * fallback value if the attribute does not exist.  This will return False if the attribute does
     * exist, but it is not the string "True" (ignoring case).
    */
    public static boolean getNodeAttributeAsBool(Node node, String attrname, boolean fallback) {
        boolean result = fallback;

        String attrStr = getNodeAttribute(node, attrname, null);
        if(null != attrStr)
            result = Boolean.parseBoolean(attrStr);

        return result;
    }


    /* Strip off trailing decimal digits from the given string and return the result.  This is useful
     * for getting the basename of a peripheral.  For example, "ADC1" would return "ADC" and "GMAC"
     * would just return "GMAC".
     */
    public static String getInstanceBasename(String instance) {
        String basename = "";

        if(null != instance  &&  !instance.isEmpty()) {
            int basesplit = instance.length()-1;
            while(basesplit > 0  &&  Character.isDigit(instance.charAt(basesplit)))
                --basesplit;

            basename = instance.substring(0, basesplit+1);
        }

        return basename;
    }

    /* Return today's date with the given date format.  See the Java docs for SimpleDateFormat for
     * what the format string should contain.  Note that the format string is case-sensive ("m" is 
     * for minutes and "M" is for months, for example) and that the number of successive letters may
     * mean different things.  For example, "M" or "MM" will give you the month as a number (the 
     * latter will always be two digits), "MMM" will give you the 3-letter abbreviation for the 
     * month, and "MMMM" or more will give you the full month name.
    */
    public static String todaysDate(String format) {
        Date date = new Date();
        SimpleDateFormat datefmt = new SimpleDateFormat(format);

        return datefmt.format(date);
    }
    
    /* Return a string that can let users know on what day a file was generated by this tool.
     */
    public static String generatedByString() {
        return ("Generated by PIC32 Stuff Generator MPLAB X plugin on " + todaysDate("dd MMM yyyy") +
                ". Find it at https://github.com/jdeguire/generatePic32SpecificStuff .");
    }

    /* Return the copyright and license string for this tool and the generated file.  Note that the
     * output files should also contain Microchip's license because the generated files are based
     * on those that come with Microchip's XC32 toolchain.
     */
    public static String generatorLicenseString() {
        return ("Copyright (c)" + todaysDate("yyyy") + ", Jesse DeGuire\n" +
                "All rights reserved.\n" +
                "\n" +
                "Redistribution and use in source and binary forms, with or without " +
                "modification, are permitted provided that the following conditions are met:\n" +
                "\n" +
                "* Redistributions of source code must retain the above copyright notice, this\n" +
                "  list of conditions and the following disclaimer.\n" +
                "\n" +
                "* Redistributions in binary form must reproduce the above copyright notice,\n" +
                "  this list of conditions and the following disclaimer in the documentation\n" +
                "  and/or other materials provided with the distribution.\n" +
                "\n" +
                "* Neither the name of the copyright holder nor the names of its\n" +
                "  contributors may be used to endorse or promote products derived from\n" +
                "  this software without specific prior written permission.\n" +
                "\n" +
                "THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" " +
                "AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE " +
                "IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE " +
                "DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE " +
                "FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL " +
                "DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR " +
                "SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER " +
                "CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, " +
                "OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE " +
                "OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.");
    }

    /* This is the license as was given in Microchip's generated processor-specific files.  This 
     * should be included in link script and MIPS header files that this generates because these 
     * files are based on the ones Microchip provides with XC32.
     */
    public static String microchipLicenseString() {
        return ("Copyright (c) 2018, Microchip Technology Inc. and its subsidiaries (\"Microchip\")\n" +
                "All rights reserved.\n" +
                "\n" +
                "This software is developed by Microchip Technology Inc. and its " +
                "subsidiaries (\"Microchip\").\n" +
                "\n" +
                "Redistribution and use in source and binary forms, with or without " +
                "modification, are permitted provided that the following conditions are met:\n" +
                "\n" +
                "1.      Redistributions of source code must retain the above copyright\n" +
                "        notice, this list of conditions and the following disclaimer.\n" +
                "2.      Redistributions in binary form must reproduce the above\n" +
                "        copyright notice, this list of conditions and the following\n" +
                "        disclaimer in the documentation and/or other materials provided\n" +
                "        with the distribution.\n" +
                "3.      Microchip's name may not be used to endorse or promote products\n" +
                "        derived from this software without specific prior written\n" +
                "        permission.\n" +
                "\n" +
                "THIS SOFTWARE IS PROVIDED BY MICROCHIP \"AS IS\" AND ANY EXPRESS OR IMPLIED " +
                "WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF " +
                "MERCHANTABILITY AND FITNESS FOR PURPOSE ARE DISCLAIMED. IN NO EVENT " +
                "SHALL MICROCHIP BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, " +
                "EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING BUT NOT LIMITED TO " +
                "PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA OR PROFITS; " +
                "OR BUSINESS INTERRUPTION) HOWSOEVER CAUSED AND ON ANY THEORY OF LIABILITY, " +
                "WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR " +
                "OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF " +
                "ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.");
    }

    /* This is the license as was given in Atmel's generated processor-specific files.  This 
     * should be included in Atmel header files that this generates because these files are based 
     * on the ones Microchip provides with XC32 for the SAM devices.
     */
    public static String apacheLicenseString() {
        return ("Copyright (c) 2018 Microchip Technology Inc.\n" +
                "\n" +
                "SPDX-License-Identifier: Apache-2.0\n" +
                "\n" +
                "Licensed under the Apache License, Version 2.0 (the \"License\"); you may " +
                "not use this file except in compliance with the License. " +
                "You may obtain a copy of the License at \n" +
                "\n" +
                "http://www.apache.org/licenses/LICENSE-2.0" + "\n" +
                "\n" +
                "Unless required by applicable law or agreed to in writing, software " +
                "distributed under the License is distributed on an AS IS BASIS, WITHOUT " +
                "WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. " +
                "See the License for the specific language governing permissions and " +
                "limitations under the License.");
    }
}
