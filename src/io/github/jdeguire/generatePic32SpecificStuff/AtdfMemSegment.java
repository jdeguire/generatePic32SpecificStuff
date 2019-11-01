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

import org.w3c.dom.Node;

/**
 * This encapsulates the info from "memory-segment" XML nodes in ATDF documents, which contain info 
 * about how the address spaces on the device are used.
 */
public class AtdfMemSegment {
    private final String name_;
    private final long startAddr_;
    private final long totalSize_;
    private final long pageSize_;

    public AtdfMemSegment(Node atdfNode) {
        name_ = Utils.getNodeAttribute(atdfNode, "name", "");
        startAddr_ = Utils.getNodeAttributeAsLong(atdfNode, "start", 0);
        totalSize_ = Utils.getNodeAttributeAsLong(atdfNode, "size", 0);
        pageSize_ = Utils.getNodeAttributeAsLong(atdfNode, "pagesize", 0);
    }

    /* Get the name of the memory segment, which will be formatted like a C macro.
     */
    public String getName() {
        return name_;
    }

    /* Get the starting address of the segment.
     */
    public long getStartAddress() {
        return startAddr_;
    }

    /* Get the total size in bytes of the segment.
     */
    public long getTotalSize() {
        return totalSize_;
    }

    /* Get the page size of the segment in bytes.  This applies only to flash segments and will 
     * be 0 for other types of memory.
     */
    public long getPageSize() {
        return pageSize_;
    }
}
