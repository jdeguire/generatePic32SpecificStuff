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

import com.microchip.crownking.edc.DCR;
import java.util.List;

/**
 * This is a class of static methods used by the different MIPS code generators.
 */
public class MipsCommon {

    // This is the physical address at which the CPU begin execution when it is reset.
    public static final long MIPS_RESET_PHYS_ADDR  = 0x1FC00000L;
    

    /* Return a name that would be used in a linker script or "section" attribute for the section
     * of memory the DCR would occupy.
     */
    public static String getDcrMemorySectionName(DCR dcr) {
        return "config_" + dcr.getName();
    }

    /* Walk through the list of all of the target's memory regions and add the ones that are most
     * useful for linker scripts and header files, possibly modifying them along the way.  This will
     * also add regions for the device config registers.
     */
    public static void addStandardMemoryRegions(List<LinkerMemoryRegion> lmrList,
                                                TargetDevice target, 
                                                InterruptList intList, 
                                                List<DCR> dcrList) {
        List<LinkerMemoryRegion> targetRegions = target.getMemoryRegions();

        for(LinkerMemoryRegion region : targetRegions) {
            switch(region.getType()) {
                case BOOT:
                    if(MIPS_RESET_PHYS_ADDR == region.getStartAddress()) {
                        addBootRegionsBySize(lmrList, region.getLength());
                    } else {
                        region.setAsKseg1Region();
                        lmrList.add(region);
                    }
                    break;
                case CODE:
                    if(region.getName().equalsIgnoreCase("code")) {
                        region.setName("kseg0_program_mem");
                        region.setAccess(LinkerMemoryRegion.EXEC_ACCESS | LinkerMemoryRegion.READ_ACCESS);
                        region.setAsKseg0Region();
                        lmrList.add(region);
                    }
                    break;
                case SRAM:
                    if(region.getName().equalsIgnoreCase("kseg1_data_mem")) {
                        region.setAccess(LinkerMemoryRegion.NOT_EXEC_ACCESS | LinkerMemoryRegion.WRITE_ACCESS);
                        region.setAsKseg1Region();
                        lmrList.add(region);
                    } else if(region.getName().equalsIgnoreCase("kseg0_data_mem")) {
                        region.setAccess(LinkerMemoryRegion.NOT_EXEC_ACCESS | LinkerMemoryRegion.WRITE_ACCESS);
                        region.setAsKseg0Region();
                        lmrList.add(region);
                    }
                    break;
                case EBI:
                case SQI:
                {
                    LinkerMemoryRegion kseg2_region = new LinkerMemoryRegion(region);
                    LinkerMemoryRegion kseg3_region = new LinkerMemoryRegion(region);

                    kseg2_region.setName("kseg2_" + region.getName());
                    kseg2_region.setAsKseg2Region();
                    lmrList.add(kseg2_region);

                    kseg3_region.setName("kseg3_" + region.getName());
                    kseg3_region.setAsKseg3Region();
                    lmrList.add(kseg3_region);

                    break;
                }
                case SDRAM:
                    region.setAsKseg0Region();
                    lmrList.add(region);
                    break;
                case FUSE:
                case PERIPHERAL:
                    region.setAsKseg1Region();
                    lmrList.add(region);
                    break;
                default:
                    break;
            }
        }

        if(!intList.usesVariableOffsets()) {
            long startAddr = intList.getDefaultBaseAddress();
            long sizePerVector = 32;

            if(0 == startAddr)
                startAddr = 0x9D000000;

            // MicroMIPS-only devices have smaller vectors.
            if(target.supportsMicroMipsIsa()  &&  !target.supportsMips32Isa())
                sizePerVector = 8;
                
            // The vectors start at 0x200.  Before that are things like the general exception vector
            // and TLB refill exception.
            long endAddr = startAddr + (0x200 + (sizePerVector * (intList.getLastVectorNumber() + 1)));

            lmrList.add(new LinkerMemoryRegion("exception_mem", 0, startAddr, endAddr));
        }

        // Each device config register has its own region, which makes it easier to place the
        // registers in the correct spots when the user specifies their values in code.
        for(DCR dcr : dcrList) {
            long dcrAddr = target.getRegisterAddress(dcr);
            LinkerMemoryRegion dcrRegion = new LinkerMemoryRegion(getDcrMemorySectionName(dcr),
                                                                  0,
                                                                  dcrAddr,
                                                                  dcrAddr + 4);
            dcrRegion.setAsKseg1Region();
            lmrList.add(dcrRegion);
        }
    }


    /* Different MIPS device families have differently-sized boot memory regions and different ways
     * the debugger reserves memory in them.  This method will create and add memory regions to the
     * given list based on the given boot memory size.
     */
    private static void addBootRegionsBySize(List<LinkerMemoryRegion> lmrList, long bootSize) {
        /* The size of the boot flash depends on the device subfamily and is the main factor in how 
         * the regions are laid out.  There are 4 different sizes of boot flash--3kB, 12kB, 20kB, 
         * and 80kB--so we can use that to figure out what we need.  The given region will be a bit 
         * smaller than those sizes because the flash has other stuff, like the config registers.
         */

        if(bootSize <= (3 * 1024)) {
            // PIC32MM and small PIC32MX
            lmrList.add(new LinkerMemoryRegion("debug_exec_mem", 0, 0x9FC00490L, 0x9FC00BF0L));
            lmrList.add(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC00490L, 0x9FC00490L));
            lmrList.add(new LinkerMemoryRegion("kseg1_boot_mem", 0, 0xBFC00000L, 0xBFC00490L));
        } else if(bootSize <= (12 * 1024)) {
            // Large PIC32MX
            lmrList.add(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC00490L, 0x9FC00E00L));
            lmrList.add(new LinkerMemoryRegion("kseg1_boot_mem", 0, 0xBFC00000L, 0xBFC00490L));
            lmrList.add(new LinkerMemoryRegion("debug_exec_mem", 0, 0xBFC02000L, 0xBFC02FF0L));
        } else if(bootSize <= (20 * 1024)) {
            // PIC32MK
            // The gap just before 0x9FC004B0 is present in the XC32 scripts, so we'll
            // keep it here for now.  The same goes for the empty kseg0_boot_mem region.
            lmrList.add(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC004B0L, 0x9FC004B0L));
            lmrList.add(new LinkerMemoryRegion("debug_exec_mem", 0, 0x9FC20490L, 0x9FC23FB0L));
            lmrList.add(new LinkerMemoryRegion("kseg1_boot_mem", 0, 0xBFC00000L, 0xBFC00490L));
            lmrList.add(new LinkerMemoryRegion("kseg1_boot_mem_4B0", 0, 0xBFC004B0L, 0xBFC03FB0L));
        } else {
            // PIC32MZ
            // The gap just before 0x9FC004B0 is present in the XC32 scripts, so we'll
            // keep it here for now.  The same goes for the empty kseg0_boot_mem region.
            // The PIC32MZ does not need to reserve flash for the debugger.
            lmrList.add(new LinkerMemoryRegion("kseg0_boot_mem", 0, 0x9FC004B0L, 0x9FC004B0L));
            lmrList.add(new LinkerMemoryRegion("kseg1_boot_mem", 0, 0xBFC00000L, 0xBFC00490L));
            lmrList.add(new LinkerMemoryRegion("kseg1_boot_mem_4B0", 0, 0xBFC004B0L, 0xBFC0FF00L));
        }
   }
}
