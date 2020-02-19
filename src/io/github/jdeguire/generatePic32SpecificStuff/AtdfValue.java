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

import org.w3c.dom.Node;

/**
 * This encapsulates the info from various XML nodes in the ATDF document that contain macro names 
 * and value that pertain to the device itself or its peripheral instances.  The nodes have a few 
 * different names ("parameter", "property", "value"), but they all have the same attributes that
 * we care about.
 */
public class AtdfValue {
    private final String name_;
    private final String value_;
    private final String caption_;


    public AtdfValue(Node atdfNode) {
       name_ = Utils.getNodeAttribute(atdfNode, "name", "");
       value_ = Utils.getNodeAttribute(atdfNode, "value", "");
       caption_ = Utils.getNodeAttribute(atdfNode, "caption", "");
    }

    /* Get the value's name, which will be formatted like a C macro. 
     */
    public String getName() {
        return name_;
    }

    /* Get the value itself as a string, which will be usable in C code.
     */
    public String getValue() {
        return value_;
    }

    /* Get the value's caption, which would be a C comment describing the parameter. 
     */
    public String getCaption() {
        return caption_;
    }
}
