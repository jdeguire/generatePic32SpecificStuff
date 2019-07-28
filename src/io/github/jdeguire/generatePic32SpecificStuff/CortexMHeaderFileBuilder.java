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

import com.microchip.crownking.edc.SFR;
import com.microchip.mplab.crownkingx.xPIC;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.w3c.dom.Node;

/**
 * A subclass of the HeaderFileBuilder that handles ARM Cortex-M devices.
 */
public class CortexMHeaderFileBuilder extends HeaderFileBuilder {

    private HashMap<String, ArrayList<SFR>> peripheralSFRs_ = new HashMap<>(20);

    CortexMHeaderFileBuilder(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) throws java.io.FileNotFoundException {
        String basename = target.getDeviceName();

        createNewHeaderFile(target);

        peripheralSFRs_.clear();
        populateSFRs(target);


        outputLicenseHeader();
    }

    private void populateSFRs(TargetDevice target) {
        // The "edc:" prefix is not needed because we're going through the 'xPIC' object.
        Node peripheralListNode = target.getPic().first("PeripheralList");

        // The "edc:" prefix is needed here because we're going through the Node object.
        List<Node> peripheralNodes = Utils.filterAllChildNodes(peripheralListNode, "edc:Peripheral", 
                                                               "edc:cname", null);

        // Initialize the map with the listed peripherals.
        for(Node node : peripheralNodes) {
            Node attr = node.getAttributes().getNamedItem("edc:cname");
            peripheralSFRs_.put(attr.getNodeValue(), new ArrayList<SFR>(10));
        }

        // Now, get all of the SFRs and put them with their member peripherals.
        List<SFR> allSFRs = target.getSFRs();
        for(SFR sfr : allSFRs) {
            // This gets the member peripherals from the "ltx:memberofperipheral" XML attribute.
            List<String> peripheralIDs = sfr.getPeripheralIDs();

            // Some registers may be missing the peripheral ID (ICTR on SAME54 for example), but
            // they may not be needed since the particular ones are system control ones and thus
            // covered by the CMSIS headers.
            if(!peripheralIDs.isEmpty()) {
                String peripheralId = peripheralIDs.get(0);
                ArrayList<SFR> peripheralList = peripheralSFRs_.get(peripheralId);

                if(null != peripheralList) {
                    peripheralList.add(sfr);
                }
            }
        }
    }
}
