/* Copyright (c) 2020, Jesse DeGuire
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

import java.io.PrintWriter;
import org.xml.sax.SAXException;

/**
 * This is a base class for building device-specific header files that contain register definitions,
 * part-specific macros, and interrupt vector names.
 */
public abstract class HeaderFileGenerator {
    protected String basepath_;
    protected PrintWriter writer_;

    /* Create a new builder that can be used to generate scripts for multiple devices, all rooted at
     * the given base path (which should be a directory).  Use getHeaderRelativePath() to
     * determine where a particular target's script will be located.
     */
    public HeaderFileGenerator(String basepath) {
        basepath_ = basepath;
    }

    /* Generate a linker script for the given device, using its name for the subdirectory and the
     * name of the script itself.
     */
    abstract public void generate(TargetDevice target) 
                         throws java.io.FileNotFoundException, SAXException;

    /* Return the device name formatted for use as a header name.
     */
    public String getDeviceNameForHeader(TargetDevice target) {
        return target.getDeviceName().toLowerCase();
    }

    /* Return the path to the header file, including the linker script file itself, used in the
     * generate() method.  The returned path is relative to the base path given in the constructor 
     * for this class.
     */
    public String getHeaderRelativePath(TargetDevice target) {
        return getDeviceNameForHeader(target) + ".h";
    }

    /* Create a new header file based on the target, updating the local PrintWriter with the 
     * new one.  This creates a version of the PrintWriter that always uses Unix line separators ('\n').
     */
    protected void createNewHeaderFile(TargetDevice target)
                            throws java.io.FileNotFoundException {
        writer_ = Utils.createUnixPrintWriter(basepath_ + "/" + getHeaderRelativePath(target));
    }

    /* Close the header file, which ensures that the writer's contents have been flushed to disk.  
     * Do this at the end of the your generate() method.
     */
    protected void closeHeaderFile() {
        writer_.close();
    }
    
    /* Add the license header to the header file opened by the given writer.  This will output the
     * license for this generator and Microchip's BSD license.
     */
    protected void outputLicenseHeaderBsd(PrintWriter writer) {
        String header = (Utils.generatedByString() + "\n\n" +
                         Utils.generatorLicenseString() + "\n\n" +
                         "                                               ******\n\n" + 
                         "This file is generated based on source files included with Microchip " +
                         "Technology's XC32 toolchain.  Microchip's license is reproduced below:\n\n" +
                         Utils.microchipBsdLicenseString());

        Utils.writeMultilineCComment(writer, 0, header);
        writer.println();
    }

    /* Add the license header to the header file opened by the given writer.  This will output the
     * license for this generator and Microchip's/Atmel's Apache license.
     */
    protected void outputLicenseHeaderApache(PrintWriter writer) {
        String header = (Utils.generatedByString() + "\n\n" +
                         Utils.generatorLicenseString() + "\n\n" +
                         "                                               ******\n\n" + 
                         "This file is generated based on source files included with Microchip " +
                         "Technology's XC32 toolchain.  Microchip's license is reproduced below:\n\n" +
                         Utils.apacheLicenseString());

        Utils.writeMultilineCComment(writer, 0, header);
        writer.println();
    }

    /* Add the license header to the header file opened by the given writer.  This will output the 
     * license for this generator and Microchip's standard "use with Microchip products" license.
     */
    protected void outputLicenseHeaderMicrochipStandard(PrintWriter writer) {
        String header = (Utils.generatedByString() + "\n\n" +
                         Utils.generatorLicenseString() + "\n\n" +
                         "                                               ******\n\n" + 
                         "This file is generated based on source files included with Microchip " +
                         "Technology's XC32 toolchain.  Microchip's license is reproduced below:\n\n" +
                         Utils.microchipStdLicenseString());

        Utils.writeMultilineCComment(writer, 0, header);
        writer.println();
    }

    /* These next four are just convenience methods used to output idefs and endifs that block out
     * sections of header file based on wether or not an assembler is running.
     */
    protected void writeNoAssemblyStart(PrintWriter writer) {
        writer.println("#ifndef __ASSEMBLER__");
    }

    protected void writeNoAssemblyEnd(PrintWriter writer) {
        writer.println("#endif /* ifndef __ASSEMBLER__ */");
    }

    protected void writeAssemblyStart(PrintWriter writer) {
        writer.println("#ifdef __ASSEMBLER__");
    }

    protected void writeAssemblyEnd(PrintWriter writer) {
        writer.println("#endif /* ifdef __ASSEMBLER__ */");
    }

    /* Make a C macro of the form "#define <name>              <value>  / * <desc> * /"
     *
     * Note that value is padded out to 36 spaces minimum and that the spaces between the '/' and
     * '*' surrounding the description are not present in the output.
     */
    protected String makeStringMacro(String name, String value, String desc) {
        String macro = "#define " + name;

        if(null != value  &&  !value.isEmpty()) {
            macro = Utils.padStringWithSpaces(macro, 44, 4);
            macro += value;

            if(null != desc  &&  !desc.isEmpty()) {
                macro = Utils.padStringWithSpaces(macro, 64, 4);
                macro += "/* " + desc + " */";
            }
        }

        return macro;
    }

    /* Like above, but also writes it using the given PrintWriter.
     */
    protected void writeStringMacro(PrintWriter writer, String name, String value, String desc) {
        writer.println(makeStringMacro(name, value, desc));
    }
}
