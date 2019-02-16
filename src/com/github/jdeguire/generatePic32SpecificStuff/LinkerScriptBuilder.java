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

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

/**
 * This is a base class for building default linker scripts for the different target devices.
 */
public abstract class LinkerScriptBuilder {

    /* This will represent a single memory region that the linker script will contain in its MEMORY
     * section.  This is here mostly for convience and so we can sort and search regions.
     */
    protected class LinkerMemoryRegion implements Comparable<LinkerMemoryRegion> {

        public static final int READ_ACCESS = 1;
        public static final int WRITE_ACCESS = 2;
        public static final int EXEC_ACCESS = 4;
        public static final int NOT_EXEC_ACCESS = 8;
        
        private String name_;
        private int access_;
        private long startAddr_;
        private long endAddr_;

        LinkerMemoryRegion(String name, int access, long start, long end) {
            name_ = name;
            access_ = access & 0x07;
            startAddr_ = start & 0xFFFFFFFF;
            endAddr_ = end & 0xFFFFFFFF;
        }

        /* Use this one when getting the addresses as strings from the MPLAB X database, which return
         * strings as read from XML files.
         */
        LinkerMemoryRegion(String name, int access, String start, String end) {
            this(name, access, (long)Long.decode(start), (long)Long.decode(end));
        }

        /* Like above, but also ORs a mask onto the addresses.  Use this for MIPS devices when you
         * need to put a region into a particular kernel segment.
         */
        LinkerMemoryRegion(String name, int access, String start, String end, long or_mask) {
            this(name, access, (long)Long.decode(start) | or_mask, (long)Long.decode(end) | or_mask);
        }

        
        @Override
        public String toString() {
            String accessStr = "";

            if(0 != access_)
            {
                accessStr += "(";

                if(0 != (access_ & READ_ACCESS))
                    accessStr += "r";
                if(0 != (access_ & WRITE_ACCESS))
                    accessStr += "w";
                if(0 != (access_ & EXEC_ACCESS))
                    accessStr += "x";
                if(0 != (access_ & NOT_EXEC_ACCESS))
                    accessStr += "!x";

                accessStr += ")";
            }

            return String.format("%" + (32 - accessStr.length()) + "s%s : ORIGIN = 0x%08X, LENGTH = 0x%X",
                                 name_, access_, startAddr_, (endAddr_ - startAddr_));
        }

        @Override
        public int compareTo(LinkerMemoryRegion other) {
            if(startAddr_ > other.startAddr_)
                return 1;
            else if(startAddr_ < other.startAddr_)
                return -1;
            else
                return 0;
        }
    }

    LinkerScriptBuilder() {
        // Nothing to do
    }

    abstract public void generate(String filepath, TargetDevice target);
    

    /* Find the region with the given name and return it or return null if no such region could be 
     * found.
     */
    protected LinkerMemoryRegion findRegionByName(List<LinkerMemoryRegion> list, String name) {
        for(LinkerMemoryRegion region : list) {
            if(name.equals(region.name_))
                return region;
        }

        return null;
    }

    /* Sort the list of memory regions based on their starting address, with lower address coming 
     * before higher addresses.
     */
    protected void sortRegionList(List<LinkerMemoryRegion> list) {
        Collections.sort(list);
    }
}
