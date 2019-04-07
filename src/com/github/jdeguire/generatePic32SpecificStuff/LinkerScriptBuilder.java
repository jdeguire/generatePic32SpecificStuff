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

package com.github.jdeguire.generatePic32SpecificStuff;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This is a base class for building default linker scripts for the different target devices.
 */
public abstract class LinkerScriptBuilder {

    protected ArrayList<LinkerMemoryRegion> regions_;
    protected String basepath_;
    
    /* Create a new builder that can be used to generate scripts for multiple devices, all rooted at
     * the given base path (which should be a directory).  For example, if the base path is "example/",
     * then the linker sripts will be output at "example/devicename/devicename.ld" or something
     * similar.
     */
    LinkerScriptBuilder(String basepath) {
        regions_ = new ArrayList<>();
        basepath_ = basepath;
    }

    /* Generate a linker script for the given device, using its name for the subdirectory and the
     * name of the script itself.
     */
    abstract public void generate(TargetDevice target) throws java.io.FileNotFoundException;


    /* Create a new linker script file using the given subdirectory and file names, returning a 
     * PrintWriter that is used to write text to it.  This creates a version of the PrintWrite that 
     * always uses Unix line separators ('\n').  This will add the ".ld" extension to the filename.
     */
    protected PrintWriter createNewLinkerFile(String subdirName, String filename)
                            throws java.io.FileNotFoundException {
        File temp = new File(basepath_ + subdirName + File.separator + filename + ".ld");
        temp.getParentFile().mkdirs();
        
        return new PrintWriter(temp) {
                        @Override
                        public void println() {
                            write('\n');
                        }
                    };
    }


    protected void addMemoryRegion(LinkerMemoryRegion region) {
        regions_.add(region);
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
    protected void addMemoryRegionCommand(PrintWriter writer) {
        writer.println("MEMORY");
        writer.println("{");

        sortRegionList();

        for(LinkerMemoryRegion r : regions_) {
            writer.println("  " + r.toString());
        }

        writer.println("}");
        writer.println("");
    }
    
    /* Add the license header to the linker file opened by the given writer.
     */
    protected void addLicenseHeader(PrintWriter writer) {
        writer.println("/* License header to be added later. */");
        writer.println("");
    }
}
