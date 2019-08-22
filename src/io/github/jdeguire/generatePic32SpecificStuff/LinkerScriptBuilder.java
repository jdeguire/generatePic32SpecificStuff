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
import java.util.ArrayList;
import java.util.Collections;

/**
 * This is a base class for building default linker scripts for the different target devices.
 */
public abstract class LinkerScriptBuilder {

    protected ArrayList<LinkerMemoryRegion> regions_;
    protected String basepath_;
    protected PrintWriter writer_;

    /* Create a new builder that can be used to generate scripts for multiple devices, all rooted at
     * the given base path (which should be a directory).  Use getLinkerScriptRelativePath() to
     * determine where a particular target's script will be located.
     */
    LinkerScriptBuilder(String basepath) {
        regions_ = new ArrayList<>();
        basepath_ = basepath;
    }

    /* Generate a linker script for the given device.
     */
    abstract public void generate(TargetDevice target) throws java.io.FileNotFoundException;

    /* Return the path to the linker script, including the linker script file itself, used in the
     * generate() method.  The returned path is relative to the base path given in the constructor 
     * for this class.
     */
    public String getLinkerScriptRelativePath(TargetDevice target) {
        String devicename = target.getDeviceName();
        String pathname;

        if(target.isMips32()) {
            if(devicename.startsWith("PIC32")) {
                devicename = devicename.substring(3);
            }

            pathname = devicename + "/p" + devicename + ".ld";
        } else {
            if(devicename.startsWith("SAM")) {
                devicename = "AT" + devicename;
            }

            pathname = devicename + "/" + devicename + ".ld";                
        }

        return pathname;
    }
    
    /* Create a new linker script file based on the target, updating the local PrintWriter with the 
     * new one.  This creates a version of the PrintWrite that always uses Unix line separators ('\n').
     */
    protected void createNewLinkerFile(TargetDevice target)
                            throws java.io.FileNotFoundException {
        writer_ = Utils.createUnixPrintWriter(basepath_ + "/" + getLinkerScriptRelativePath(target));
    }

    /* Close the linker file, which ensures that the writer's contents have been flushed to disk.  
     * Do this at the end of the your generate() method.
     */
    protected void closeLinkerFile() {
        writer_.close();
    }


    protected void addMemoryRegion(LinkerMemoryRegion region) {
        regions_.add(region);
    }
    
    protected void clearMemoryRegions() {
        regions_.clear();
    }

    /* Find the region with the given name and return it or return null if no such region could be 
     * found.
     */
    protected LinkerMemoryRegion findRegionByName(String name) {
        for(LinkerMemoryRegion region : regions_) {
            if(name.equals(region.getName()))
                return region;
        }

        return null;
    }

    /* Sort the list of memory regions based on their starting address, with lower address coming 
     * before higher addresses.
     */
    protected void sortRegionList() {
        Collections.sort(regions_);
    }


    /* Sort the memory regions by starting address and add them to the linker script in a MEMORY
     * command.
     */
    protected void outputMemoryRegionCommand() {
        writer_.println("MEMORY");
        writer_.println("{");

        sortRegionList();

        for(LinkerMemoryRegion r : regions_) {
            writer_.println("  " + r.toString());
        }

        writer_.println("}");
        writer_.println();
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
