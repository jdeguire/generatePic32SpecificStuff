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
 * This encapsulates the info from the "signal" XML nodes found in ATDF documents, which appear to 
 * contain info about the pins used by a particular peripheral instance.
 */
public class AtdfSignal {
    private final String group_;
    private final String index_;
    private final String function_;
    private final String pad_;
    private final String ioset_;

    public AtdfSignal(Node atdfNode) {
        group_ = Utils.getNodeAttribute(atdfNode, "group", "");
        index_ = Utils.getNodeAttribute(atdfNode, "index", "");
        function_ = Utils.getNodeAttribute(atdfNode, "function", "");
        pad_ = Utils.getNodeAttribute(atdfNode, "pad", "");
        ioset_ = Utils.getNodeAttribute(atdfNode, "ioset", "");
    }

    /* Get the signal group, which indicates the signal's function (Tx, Rx, Ain, etc.).
     */
    public String getGroup() {
        return group_;
    }

    /* Get the signal index, which is a channel number (such as AIN0, AIN1, etc.).
     */
    public String getIndex() {
        return index_;
    }

    /* Get the signal function, which appears related to pin muxing.
     */
    public String getFunction() {
        return function_;
    }

    /* Get the signal pad, which is the IO port (PA0, PB6, etc.) of the pin using this signal.
     */
    public String getPad() {
        return pad_;
    }

    /* Get the signal io set, which may be related to alternate pin configurations.
     */
    public String getIoset() {
        return ioset_;
    }
}

