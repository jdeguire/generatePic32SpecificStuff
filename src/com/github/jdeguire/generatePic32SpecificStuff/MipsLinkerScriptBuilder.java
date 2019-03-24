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

import com.microchip.crownking.mplabinfo.FamilyDefinitions.SubFamily;
import com.microchip.mplab.crownkingx.xMemoryPartition;
import java.io.PrintWriter;
import java.util.List;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * A subclass of the LinkerScriptBuilder that handles MIPS devices.
 */
public class MipsLinkerScriptBuilder extends LinkerScriptBuilder {

    // OR these with a physical address to convert it into one of the 4 kernel segment addresses 
    // used in MIPS.
    private static final long KSEG0_MASK = 0x80000000;
    private static final long KSEG1_MASK = 0xA0000000;
    private static final long KSEG2_MASK = 0xC0000000;
    private static final long KSEG3_MASK = 0xE0000000;

    // This is the physical address at which the CPU begin execution when it is reset.
    private static final long MIPS_RESET_PHYS_ADDR  = 0x1FC00000;


    MipsLinkerScriptBuilder(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) throws java.io.FileNotFoundException {
        String basename = target.getDeviceName().substring(3);
        PrintWriter writer = createNewLinkerFile(basename, "p" + basename);

        populateMemoryRegions(target);
    }


    /* Different MIPS devices families have differently-sized boot memory regions and different ways
     * the debugger reserves memory in them.  We have to figure this our by the device family and 
     * create the regions needed manually.
     */
    private void setupBootRegionByFamily(TargetDevice target, LinkerMemoryRegion lmr) {
        if(SubFamily.PIC32MZ == target.getSubFamily()) {
            
        } else {
        }
    }
    
    private void populateMemoryRegions(TargetDevice target) {
        xMemoryPartition mainPartition = target.getPic().getMainPartition();
        NamedNodeMap attributes;
        String regionId;
        String beginAddr;
        String endAddr;
        LinkerMemoryRegion lmr;

        List<LinkerMemoryRegion> targetRegions = target.getMemoryRegions();

        for(LinkerMemoryRegion region : targetRegions) {
            
        }
        
/*        for(Node bootRegion : mainPartition.getBootConfigRegions()) {
            attributes = bootRegion.getAttributes();
            regionId = attributes.getNamedItem("edc:regionid").getNodeValue();
            beginAddr = attributes.getNamedItem("edc:beginaddr").getNodeValue();
            endAddr = attributes.getNamedItem("edc:endaddr").getNodeValue();

            if(Long.decode(beginAddr) == MIPS_RESET_PHYS_ADDR) {
                lmr = new LinkerMemoryRegion(regionId, 0, beginAddr, endAddr);
                setupBootRegionByFamily(target, lmr);
            } else {
                lmr = new LinkerMemoryRegion(regionId, 0, beginAddr, endAddr, KSEG1_MASK);
                addMemoryRegion(lmr);
            }
        }

        for(Node codeRegion : mainPartition.getCodeRegions()) {
            attributes = codeRegion.getAttributes();
            regionId = attributes.getNamedItem("edc:regionid").getNodeValue();
            beginAddr = attributes.getNamedItem("edc:beginaddr").getNodeValue();
            endAddr = attributes.getNamedItem("edc:endaddr").getNodeValue();

            if(regionId.equals("code")) {
                int access = LinkerMemoryRegion.EXEC_ACCESS | LinkerMemoryRegion.READ_ACCESS;
                lmr = new LinkerMemoryRegion(regionId, access, beginAddr, endAddr, KSEG1_MASK);
                addMemoryRegion(lmr);
            }
        }

        // This actually seems to be for RAM regions despite its name.
        for(Node gprRegion : mainPartition.getGPRRegions()) {
            attributes = gprRegion.getAttributes();
            regionId = attributes.getNamedItem("edc:regionid").getNodeValue();
            beginAddr = attributes.getNamedItem("edc:beginaddr").getNodeValue();
            endAddr = attributes.getNamedItem("edc:endaddr").getNodeValue();

            int access = LinkerMemoryRegion.NOT_EXEC_ACCESS | LinkerMemoryRegion.WRITE_ACCESS;

            switch(regionId) {
                case "kseg0_data_mem":
                    lmr = new LinkerMemoryRegion(regionId, access, beginAddr, endAddr, KSEG0_MASK);
                    addMemoryRegion(lmr);
                    break;
                case "kseg1_data_mem":
                    lmr = new LinkerMemoryRegion(regionId, access, beginAddr, endAddr, KSEG1_MASK);
                    addMemoryRegion(lmr);
                    break;
                default:
                    break;
            }
        }
*/
    }
}
