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

import java.io.PrintWriter;

/**
 * This is a base class for building device-specific header files that contain register definitions,
 * part-specific macros, and interrupt vector names.
 */
public abstract class HeaderFileBuilder {
    protected String basepath_;
    protected PrintWriter writer_;

    /* Create a new builder that can be used to generate scripts for multiple devices, all rooted at
     * the given base path (which should be a directory).  Use getHeaderRelativePath() to
     * determine where a particular target's script will be located.
     */
    HeaderFileBuilder(String basepath) {
        basepath_ = basepath;
    }

    /* Generate a linker script for the given device, using its name for the subdirectory and the
     * name of the script itself.
     */
    abstract public void generate(TargetDevice target) throws java.io.FileNotFoundException;

    /* Return the path to the header file, including the linker script file itself, used in the
     * generate() method.  The returned path is relative to the base path given in the constructor 
     * for this class.
     */
    public String getHeaderRelativePath(TargetDevice target) {
        String devicename = target.getDeviceName().toLowerCase();
        String pathname;

        if(target.isMips32()) {
            if(devicename.startsWith("pic32")) {
                devicename = devicename.substring(3);
            }

            pathname = "p" + devicename + ".h";
        } else {
            if(devicename.startsWith("atsam")) {
                devicename = devicename.substring(2);
            }

            pathname = devicename + ".h";
        }

        return pathname;
    }
    
    /* Create a new header file based on the target, updating the local PrintWriter with the 
     * new one.  This creates a version of the PrintWriter that always uses Unix line separators ('\n').
     */
    protected void createNewHeaderFile(TargetDevice target)
                            throws java.io.FileNotFoundException {
        writer_ = Utils.createUnixPrintWriter(basepath_ + "/" + getHeaderRelativePath(target));
    }

    /* Add the license header to the linker file opened by the given writer.
     */
    protected void outputLicenseHeader() {
        String header = (Utils.generatedByString() + "\n\n" +
                         Utils.generatorLicenseString() + "\n\n" +
                         "This file is generated based on sources files included with Microchip " +
                         "Technology's XC32 toolchain.  Microchip's license is reproduced below:\n\n" +
                         Utils.microchipLicenseString());

        Utils.writeMultilineCComment(writer_, 0, header);
    }

}
