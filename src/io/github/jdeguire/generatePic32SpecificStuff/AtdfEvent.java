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
 * This class represents a single event within the device.  Some Atmel device peripherals have an
 * event system that allows peripherals to act independent of the CPU.  An event can be a generator,
 * which would output a signal indicating an event occurred (such as a ADC read completing), or a
 * user, which would listen for the signal from a generator and act on it (such as by starting a 
 * timer).  Not all Atmel devices had an event system.
 */
public class AtdfEvent {
    private final String name_;
    private final int index_;
    private final String owningInstance_;
    private final boolean isGenerator_;


    public AtdfEvent(Node atdfNode) {
       name_ = Utils.getNodeAttribute(atdfNode, "name", "");
       index_ = Utils.getNodeAttributeAsInt(atdfNode, "index", 0);
       owningInstance_ = Utils.getNodeAttribute(atdfNode, "module-instance", "");
       isGenerator_= atdfNode.getNodeName().equals("generator");
    }

    /* Get the event's name, which will be formatted like a C macro. 
     */
    public String getName() {
        return name_;
    }

    /* Get the event's index, which indicates the value to which this event corresponds.  This
     * would be the value of a C macro for this event.
     */
    public int getIndex() {
        return index_;
    }

    /* Get the name of the peripheral instance that owns this event.
     */
    public String getOwningInstance() {
        return owningInstance_;
    }
    
    /* Return True if this event is a generator or False if it is a user.
     */
    public boolean isGenerator() {
        return isGenerator_;
    }
}
