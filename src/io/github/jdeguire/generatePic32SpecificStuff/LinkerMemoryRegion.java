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

import org.w3c.dom.Node;

/* This will represent a single memory region that the linker script will contain in its MEMORY
 * section.  This is here mostly for convience and so we can sort and search regions.
 */
public class LinkerMemoryRegion {

    public enum Type {
        UNSPECIFIED,
        BOOT,
        CODE,
        SRAM,
        EBI,
        SQI,
        SDRAM,
        FUSE,
        PERIPHERAL
    };

    public static final int READ_ACCESS = 0x01;
    public static final int WRITE_ACCESS = 0x02;
    public static final int EXEC_ACCESS = 0x04;
    public static final int ALL_ACCESS = 0x07;
    public static final int NOT_EXEC_ACCESS = 0x08;

    private String name_;
    private int access_;
    private long startAddr_;
    private long length_;
    private Type type_;

    
    public LinkerMemoryRegion(String name, int access, long start, long end, Type type) {
        name_ = name;
        access_ = access & 0x08;
        startAddr_ = start & 0xFFFFFFFFL;
        length_ = (end & 0xFFFFFFFFL) - startAddr_;
        type_ = type;
    }

    public LinkerMemoryRegion(String name, int access, long start, long end) {
        this(name, access, start, end, Type.UNSPECIFIED);
    }

    /* Use this to create a memory region from a Node object.  It is up to the caller to ensure that
     * the passed-in Node actually represents a memory region.  Use the "get____Regions()" methods
     * in the xMemoryPartition and MemoryPartition classes to ensure this.  The 'type' depends on
     * the function called to get the region.  For example, the type for "getCodeRegions()" would 
     * be CODE.
     */
    public LinkerMemoryRegion(Node regionNode, Type type) {
        this(Utils.getNodeAttribute(regionNode, "edc:regionid", ""),
             0,
             Utils.getNodeAttributeAsLong(regionNode, "edc:beginaddr", 0),
             Utils.getNodeAttributeAsLong(regionNode, "edc:endaddr", 0),
             type);
    }

    /* Copy constructor.
     */
    public LinkerMemoryRegion(LinkerMemoryRegion other) {
        name_ = other.name_;
        access_ = other.access_;
        startAddr_ = other.startAddr_;
        length_ = other.length_;
        type_ = other.type_;
    }
    
    public String getName() {
        return name_;
    }

    public long getStartAddress() {
        return startAddr_;
    }

    public long getLength() {
        return length_;
    }

    public Type getType() {
        return type_;
    }
    
    /* Assign a new name to the region, which can be done when the region name in the device database
     * is different from the name one would use in linker scripts.
     */
    public void setName(String name) {
        name_ = name;
    }

    /* Set the access allowed to this region by applying the constants at the top of this class as
     * bit flags.  This is used for code and RAM regions to tell the linker what it is allowed to
     * put into a region.  Most regions do not need this because the linker script will explicitly
     * put the sections it needs into the right regions.
     */
    public void setAccess(int access) {
        access_ = access;
    }
    
    /* Use the following on MIPS devices to put a region into a particular part of the address space.
     */
    public void setAsKseg0Region() {
        startAddr_ = ((startAddr_ & 0x1FFFFFFFL) | 0x80000000L);
    }

    public void setAsKseg1Region() {
        startAddr_ = ((startAddr_ & 0x1FFFFFFFL) | 0xA0000000L);
    }

    public void setAsKseg2Region() {
        startAddr_ = ((startAddr_ & 0x1FFFFFFFL) | 0xC0000000L);
    }

    public void setAsKseg3Region() {
        startAddr_ = ((startAddr_ & 0x1FFFFFFFL) | 0xE0000000L);
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

        return String.format("%-" + (32 - accessStr.length()) + "s%s : ORIGIN = 0x%08X, LENGTH = 0x%X",
                             name_, accessStr, startAddr_, length_);
    }
}
