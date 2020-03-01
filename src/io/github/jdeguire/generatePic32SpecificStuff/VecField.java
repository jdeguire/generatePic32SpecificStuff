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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This is here for convenience when we create custom fields that are "vectors" of adjacent 
 * fields.  The Atmel legacy header files called structs of these "vec", so that's where the name
 * comes from.  The Microchip header files do not have per-register structs, but do have macros for
 * the individual bitfields and vecfields.
 * 
 * For example, if a register has two adjacent fields called "FIELD0" and "FIELD1" that were each a
 * a single bit wide, then the resulting vecfield would be "FIELD" and would be 2 bits wide.
 */
public class VecField extends AtdfBitfield {
    public String name_;
    public String owner_;
    public String caption_;
    public long mask_;

    VecField(String name, String owner, String caption, long mask) {
        super(null, null, null);
        name_ = name;
        owner_ = owner;
        caption_ = caption;
        mask_ = mask;
    }

    VecField() {
        this("", "", "", 0);
    }

    // Copy constructor
    VecField(AtdfBitfield other) {
        this(other.getName(), other.getOwningRegisterName(), other.getCaption(), other.getMask());
    }

    @Override
    public String getName() { return name_; }

    @Override
    public String getOwningRegisterName() { return owner_; }

    @Override
    public List<String> getModes() { 
        List<String> modes = new ArrayList<>(1);
        modes.add("DEFAULT");
        return modes; 
    }

    @Override
    public String getCaption() { return caption_; }

    @Override
    public long getMask() { return mask_; }

    @Override
    public List<AtdfValue> getFieldValues() { return Collections.<AtdfValue>emptyList(); }

    public void updateMask(long update) { mask_ |= update; }
}
