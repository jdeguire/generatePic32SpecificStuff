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

import com.microchip.crownking.Pair;
import com.microchip.crownking.edc.Bitfield;
import com.microchip.crownking.edc.DCR;
import com.microchip.crownking.edc.Mode;
import com.microchip.crownking.edc.Register;
import com.microchip.crownking.edc.SFR;
import com.microchip.crownking.mplabinfo.FamilyDefinitions;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.SubFamily;
import com.microchip.mplab.crownkingx.xMemoryPartition;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Node;

/**
 * A subclass of the HeaderFileBuilder class that handles MIPS devices.
 */
public class MipsHeaderFileBuilder extends HeaderFileBuilder {

    public MipsHeaderFileBuilder(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) throws java.io.FileNotFoundException {
        List<SFR> sfrList = target.getSFRs();
        List<DCR> dcrList = target.getDCRs();
        InterruptList interruptList = new InterruptList(target.getPic());

        createNewHeaderFile(target);
        outputLicenseHeader(writer_, false);
        outputIncludeGuardStart(target);

        // Stuff for C/C++ only
        writeNoAssemblyStart(writer_);
        outputIncludedHeaders();
        outputExternCStart();
        outputSfrDefinitions(target, sfrList);
        outputDcrDefinitions(target, dcrList);
        outputExternCEnd();

        writer_.println("#else  /* __ASSEMBLER__ */");
        writer_.println();

        // Stuff for assembler only
        outputSfrAssemblerMacros(target, sfrList);
        outputDcrAssemblerMacros(target, dcrList);

        writer_.println();
        writeNoAssemblyEnd(writer_);
        writer_.println();

        // Stuff for both C/C++ and assembler
        outputSfrFieldMacros(sfrList);
        outputDcrFieldMacros(dcrList);
        outputInterruptMacros(interruptList);
        outputPeripheralMacros(target);
        outputMemoryRegionMacros(target, interruptList);
        outputTargetFeatureMacros(target, interruptList.getNumShadowRegs());

        outputIncludeGuardEnd(target);
        closeHeaderFile();
    }


    /* Output the opening "#ifndef...#define" sequence of an include guard for this header file.
     */
    private void outputIncludeGuardStart(TargetDevice target) {
        writer_.println("#ifndef __" + target.getBaseDeviceName() + "_H");
        writer_.println("#define __" + target.getBaseDeviceName() + "_H");
        writer_.println();
    }

    /* Output the closing "#endif" of an include guard for this header file.
     */
    private void outputIncludeGuardEnd(TargetDevice target) {
        writer_.println("#endif  /* __" + target.getBaseDeviceName() + "_H */");
    }

    /* Output the opening sequence of macros for C linkage.
     */
    private void outputExternCStart() {
        writer_.println("#ifdef __cplusplus");
        writer_.println("extern \"C\" {");
        writer_.println("#endif");
        writer_.println();
    }

    /* Output the closing sequence of macros for C linkage.
     */
    private void outputExternCEnd() {
        writer_.println("#ifdef __cplusplus");
        writer_.println("} /* extern \"C\" */");
        writer_.println("#endif");
        writer_.println();
    }

    /* Output preprocessor directives to include any needed header files.
     */
    private void outputIncludedHeaders() {
        writer_.println("#include <stdint.h>");
        writer_.println();
    }

    /* Output C definitions for the SFRs in the given list.
     */
    private void outputSfrDefinitions(TargetDevice target, List<SFR> sfrList) {
        for(SFR sfr : sfrList) {
            outputRegisterDefinition(target, sfr);
        }
    }

    /* Output C definitions for the DCRs (Device Config Registers) in the given list.
     */
    private void outputDcrDefinitions(TargetDevice target, List<DCR> dcrList) {
        for(DCR dcr : dcrList) {
            outputRegisterDefinition(target, dcr);
        }
    }

    /* Output assembler macros for the SFRs in the given list, which just provide the address of the
     * register as a raw value.
     */
    private void outputSfrAssemblerMacros(TargetDevice target, List<SFR> sfrList) {
        for(SFR sfr : sfrList) {
            outputRegisterAssemblerMacro(target, sfr);
        }
    }

    /* Output assembler macros for the DCRs (Device Config Registers) in the given list, which just
     * provide the address of the register as a raw value.
     */
    private void outputDcrAssemblerMacros(TargetDevice target, List<DCR> dcrList) {
        for(DCR dcr : dcrList) {
            outputRegisterAssemblerMacro(target, dcr);
        }
    }

    /* Output field macros for the SFRs in the given list that can be used to access the bitfields
     * on the SFR.
     */
    private void outputSfrFieldMacros(List<SFR> sfrList) {
        for(SFR sfr : sfrList) {
            outputRegisterFieldMacros(sfr);
        }
    }

    /* Output field macros for the DCRs (Device Config Registers) in the given list that can be used
     * to access the bitfields on the DCR.
     */
    private void outputDcrFieldMacros(List<DCR> dcrList) {
        for(DCR dcr : dcrList) {
            outputRegisterFieldMacros(dcr);
        }
    }

    /* Output macros providing the interrupt vectors and requests (if used) for the device.
     */
    private void outputInterruptMacros(InterruptList interruptList) {
        List<InterruptList.Interrupt> vectorList = interruptList.getInterruptVectors();

        writer_.println("/* Interrupt Vector Numbers */");
        for(InterruptList.Interrupt vector : vectorList) {
            writeLengthyDecimalMacro(writer_, 
                                     "_" + vector.getName() + "_VECTOR",
                                     (long)vector.getIntNumber());
        }
        writer_.println();

        // Some PIC32MX devices have to share vectors with multiple requests because the MIPS CPU
        // at the time supported only 64 vectors, but the PIC32MX had more than 64 request sources.
        // In such a case, the request numbers are separate, so we need to output those too.
        if(interruptList.hasSeparateInterruptRequests()) {
            List<InterruptList.Interrupt> requestList = interruptList.getInterruptRequests();

            writer_.println("/* Interrupt Request Numbers */");
            for(InterruptList.Interrupt request : requestList) {
                writeLengthyDecimalMacro(writer_, 
                                         "_" + request.getName() + "_IRQ",
                                         (long)request.getIntNumber());
            }
            writer_.println();
        }
    }

    /* Output macros that indicate that a particular peripheral is present on the device as well as
     * that peripheral's base address.
     */
    private void outputPeripheralMacros(TargetDevice target) {
        List<Node> baseNodeList = getPeripheralBaseNodes(target);

        writer_.println("/* Device Peripherals */");
        for(Node node : baseNodeList) {
            String[] peripherals = Utils.getNodeAttribute(node, "ltx:baseofperipheral", "").split(" ");

            for(String periph : peripherals) {
                writeStringMacro(writer_, "_" + periph, null, null);
            }
        }
        writer_.println();

        writer_.println("/* Peripheral Base Addresses */");
        for(Node node : baseNodeList) {
            String[] peripherals = Utils.getNodeAttribute(node, "ltx:baseofperipheral", "").split(" ");
            long addr = makeKseg1Addr(Utils.getNodeAttributeAsLong(node, "edc:_addr", 0));

            for(String periph : peripherals) {
                writeLengthyHexMacro(writer_, "_" + periph + "_BASE_ADDRESS", addr);
            }
        }
        writer_.println();
    }

    /* Output macros that give the memory location and size of the default memory regions used by
     * the linker scripts generated by this tool.
     */
    private void outputMemoryRegionMacros(TargetDevice target,
                                          InterruptList interruptList) {
        ArrayList<LinkerMemoryRegion> lmrList = new ArrayList<>();
        MipsCommon.addStandardMemoryRegions(lmrList, target, interruptList, Collections.<DCR>emptyList());

        writer_.println("/* Default Memory Region Macros */");
        for(LinkerMemoryRegion lmr : lmrList) {
            String lmrMacroBase = "__" + lmr.getName().toUpperCase() + "_";

            writeLengthyHexMacro(writer_, lmrMacroBase + "BASE", lmr.getStartAddress());
            writeLengthyHexMacro(writer_, lmrMacroBase + "LENGTH", lmr.getLength());
        }

        writer_.println();
    }

    /* Output macros that code can use to query device feature, such as instruction set support or
     * the number of shadow registers.  This function needs to know the number of shadow register 
     * sets the device has, which can be retrieved from an InterruptList.
     */
    private void outputTargetFeatureMacros(TargetDevice target, int srsCount) {
        String devname = target.getDeviceName();
        String basename = target.getBaseDeviceName();

        // Output macros common to all devices.
        writeGuardedMacro(writer_, "__" + basename, "1");
        writeGuardedMacro(writer_, "__" + basename + "__", "1");
        writeGuardedMacro(writer_, "__XC", "1");
        writeGuardedMacro(writer_, "__XC__", "1");
        writeGuardedMacro(writer_, "__XC32", "1");
        writeGuardedMacro(writer_, "__XC32__", "1");
        writer_.println();

        // Output a macro to indicate device series, which is the first few character of the name.
        // The MGCxxx and MECxxx devices are a bit different here.
        String series;
        if(devname.startsWith("M")) {
            series = devname.substring(0, 3);
        } else if(devname.startsWith("USB")) {
            series = devname.substring(0, 5);
        } else {
            series = devname.substring(0, 7);
        }
        writeGuardedMacro(writer_, "__" + series, "1");
        writeGuardedMacro(writer_, "__" + series + "__", "1");

        // Output feature macros that use the name of the device to fill in.  These are used only
        // on PIC32 devices.
        if(series.startsWith("PIC32")) {
            if(series.equals("PIC32MX")) {
                String flashSize = substringWithoutLeadingZeroes(basename, 8, 11);
                writeGuardedMacro(writer_, "__PIC32_FLASH_SIZE", flashSize);
                writeGuardedMacro(writer_, "__PIC32_FLASH_SIZE__", flashSize);
                writeGuardedMacro(writer_, "__PIC32_MEMORY_SIZE", flashSize);
                writeGuardedMacro(writer_, "__PIC32_MEMORY_SIZE__", flashSize);

                String featureSet = basename.substring(4, 7);
                writeGuardedMacro(writer_, "__PIC32_FEATURE_SET", featureSet);
                writeGuardedMacro(writer_, "__PIC32_FEATURE_SET__", featureSet);

                String pinSet = "\'" + basename.substring(basename.length()-1) + "\'";
                writeGuardedMacro(writer_, "__PIC32_PIN_SET", pinSet);
                writeGuardedMacro(writer_, "__PIC32_PIN_SET__", pinSet);
            } else {
                String flashSize = substringWithoutLeadingZeroes(basename, 4, 8);
                writeGuardedMacro(writer_, "__PIC32_FLASH_SIZE", flashSize);
                writeGuardedMacro(writer_, "__PIC32_FLASH_SIZE__", flashSize);
                
                String featureSet = basename.substring(8, 10);
                String featureSet0 = featureSet.substring(0, 1);
                String featureSet1 = featureSet.substring(1, 2);
                writeGuardedMacro(writer_, "__PIC32_FEATURE_SET", "\"" + featureSet + "\"");
                writeGuardedMacro(writer_, "__PIC32_FEATURE_SET__", "\"" + featureSet + "\"");
                writeGuardedMacro(writer_, "__PIC32_FEATURE_SET0", "\'" + featureSet0 + "\'");
                writeGuardedMacro(writer_, "__PIC32_FEATURE_SET0__", "\'" + featureSet0 + "\'");
                writeGuardedMacro(writer_, "__PIC32_FEATURE_SET1", "\'" + featureSet1 + "\'");
                writeGuardedMacro(writer_, "__PIC32_FEATURE_SET1__", "\'" + featureSet1 + "\'");

                String productGroup = "\'" + basename.substring(10, 11) + "\'";
                writeGuardedMacro(writer_, "__PIC32_PRODUCT_GROUP", productGroup);
                writeGuardedMacro(writer_, "__PIC32_PRODUCT_GROUP__", productGroup);

                String pinCount = substringWithoutLeadingZeroes(basename, basename.length()-3, basename.length());
                writeGuardedMacro(writer_, "__PIC32_PIN_COUNT", pinCount);
                writeGuardedMacro(writer_, "__PIC32_PIN_COUNT__", pinCount);
            }
        }
        writer_.println();

        // Output macros that give architecture details like supported instruction sets and if the 
        // device has an FPU.
        if(target.hasL1Cache()) {
            writeGuardedMacro(writer_, "__PIC32_HAS_L1CACHE", "1");
        }
        if(target.supportsMips32Isa()) {
            writeGuardedMacro(writer_, "__PIC32_HAS_MIPS32R2", "1");
        }
        if(TargetDevice.TargetArch.MIPS32R5 == target.getArch()) {
            writeGuardedMacro(writer_, "__PIC32_HAS_MIPS32R5", "1");
        }
        if(target.supportsMicroMipsIsa()) {
            writeGuardedMacro(writer_, "__PIC32_HAS_MICROMIPS", "1");
        }
        if(target.supportsMips16Isa()) {
            writeGuardedMacro(writer_, "__PIC32_HAS_MIPS16", "1");
        }
        if(target.supportsDspR2Ase()) {
            writeGuardedMacro(writer_, "__PIC32_HAS_DSPR2", "1");
        }
        if(target.supportsMcuAse()) {
            writeGuardedMacro(writer_, "__PIC32_HAS_MCUASE", "1");
        }
        if(target.hasFpu()) {
            writeGuardedMacro(writer_, "__PIC32_HAS_FPU64", "1");
        }
        if(!series.equals("PIC32MX")) {
            writeGuardedMacro(writer_, "__PIC32_HAS_SSX", "1");
        }
        if(SubFamily.PIC32MZ == target.getSubFamily()) {
            writeGuardedMacro(writer_, "__PIC32_HAS_MMU_MZ_FIXED", "1");
        }
        if(target.supportsMicroMipsIsa()  &&  !target.supportsMips32Isa()) {
            writeGuardedMacro(writer_, "__PIC32_HAS_INTCONVS", "1");
        }

        writeGuardedMacro(writer_, "__PIC32_HAS_INIT_DATA", "1");
        writeGuardedMacro(writer_, "__PIC32_SRS_SET_COUNT", Integer.toString(srsCount));
        writer_.println();
    }

    /* Output C definitions for the given Register.  This is the implementation of the two methods
     * outputSfrDefinitions() and outputDcrDefinitions() since SFRs and DCRs are really just types
     * of Registers.
     */
    private void outputRegisterDefinition(TargetDevice target, Register register) {
        String regName = register.getName();
        long regAddr = target.getRegisterAddress(register);

        writeRegisterAddressMacro(writer_, regName, regAddr);

        if(outputModeDefinitions(register)) {
            writeRegisterBitsMacro(writer_, regName, regAddr);
        }

        // These are the CLR, SET, and INV registers that most SFRs on the PIC32 have.
        if(register.hasPortals()) {
            List<String> portalNames = register.getPortalNames();
            long portalAddr = regAddr;

            for(String portal : portalNames) {
                portalAddr += 4;
                writeRegisterAddressMacro(writer_, regName + portal, portalAddr);
            }
        }

        writer_.println();
    }

    /* Output a C union representing the different modes of the given Register.  A Mode is just a 
     * way to access the bits of a register.  That is, a register that has multi-bit fields would 
     * have a mode to access each bit in the field individually and another mode to access the whole
     * field as a single entity.
     *
     * Returns True if the Register contained Modes that could be written (ie. contained non-hidden
     * fields) or False if there were no Modes to be written.  No union declaration is output if it
     * would have been empty.
     */
    private boolean outputModeDefinitions(Register register) {
        List<Node> modeList = register.getModes();
        boolean wroteAMode = false;
        boolean needsWordMode = true;

        for(Node modeNode : modeList) {
            Mode mode = new Mode(modeNode);
            List<Bitfield> bitfieldList = getBitfieldsFromMode(register, mode, true);

            if(!bitfieldList.isEmpty()) {
                // Start our union declaration if we found a non-trivial mode.
                if(!wroteAMode) {
                    writer_.println("typedef union {");
                    wroteAMode = true;
                }

                outputBitfieldDefinitions(bitfieldList);

                // Most registers with modes have one that has a single 32-bit field called "w".
                // Not all do (DCRs in particular), so we have to check for that and add the mode
                // ourselves later if one is not present.
                if(1 == bitfieldList.size()  &&  bitfieldList.get(0).getName().equals("w")) {
                    needsWordMode = false;
                }
            }
        }

        // We need to finish our union declaration if we actually wrote any modes' bitfields.
        if(wroteAMode) {
            if(needsWordMode) {
                writer_.println("  struct {");
                writer_.println("    uint32_t w:32;");
                writer_.println("  };");
            }

            writer_.println("} " + getRegisterBitsName(register.getName()) + ";");
        }

        return wroteAMode;
    }

    /* Output a C struct representing the given list of Bitfields.  Presumably, the Bitfields
     * provided are all a part of the same Register Mode.
     */
    private void outputBitfieldDefinitions(List<Bitfield> bitfieldList) {

        writer_.println("  struct {");

        long nextPos = 0;
        for(Bitfield bitfield : bitfieldList) {
            long position = bitfield.getPlace();
            long width = bitfield.getWidth();
            long gap = position - nextPos;

            // Fill in any gaps as we go.
            if(gap > 0) {
                writer_.println("    uint32_t :" + gap + ";");
                nextPos += gap;
            }

            writer_.println("    uint32_t " + bitfield.getName() + ":" + width + ";");

            nextPos += width;
        }

        writer_.println("  };");
    }

    /* Output a macro for the given Register that provides its address.  This is the implementation 
     * of the two methods outputSfrAssemblerMacros() and outputDcrAssemblerMacros() since SFRs and 
     * DCRs are really just types of Registers.
     */
    private void outputRegisterAssemblerMacro(TargetDevice target, Register register) {
        long addr = makeKseg1Addr(target.getRegisterAddress(register));
        String regName = register.getName();

        writeStringMacro(writer_, regName, String.format("(0x%08X)", addr), null);

        // These are the CLR, SET, and INV registers that most SFRs on the PIC32 have.
        if(register.hasPortals()) {
            List<String> portalNames = register.getPortalNames();

            for(String portal : portalNames) {
                addr += 4;
                writeStringMacro(writer_, regName + portal, String.format("(0x%08X)", addr), null);
            }
        }
    }

    /* Output bitfield macros for the given Register.  This is the implementation of the two methods
     * outputSfrFieldMacros() and outputDcrFieldMacros() since SFRs and DCRs are really just types
     * of Registers.
     */
    private void outputRegisterFieldMacros(Register register) {
        List<Node> modeList = register.getModes();
        boolean wroteMacros = false;
        boolean needsWordMacros = true;

        for(Node modeNode : modeList) {
            Mode mode = new Mode(modeNode);
            List<Bitfield> bitfieldList = getBitfieldsFromMode(register, mode, true);

            if(!bitfieldList.isEmpty()) {
                wroteMacros = true;
            }

            for(Bitfield bf : bitfieldList) {
                String macroBase = "_" + register.getName() + "_" + bf.getName() + "_";
                long position = bf.getPlace();
                long mask = bf.getMask() << position;
                long width = bf.getWidth();

                writeLengthyHexMacro(writer_, macroBase + "POSITION", position);
                writeLengthyHexMacro(writer_, macroBase + "MASK", mask);
                writeLengthyHexMacro(writer_, macroBase + "LENGTH", width);
                writer_.println();
            }

            // Most registers with modes have one that has a single 32-bit field called "w".
            // Not all do (DCRs in particular), so we have to check for that and add the mode
            // ourselves later if one is not present.
            if(1 == bitfieldList.size()  &&  bitfieldList.get(0).getName().equals("w")) {
                needsWordMacros = false;
            }
        }

        if(wroteMacros  &&  needsWordMacros) {
            String macroBase = "_" + register.getName() + "_w_";

            writeLengthyHexMacro(writer_, macroBase + "POSITION", 0);
            writeLengthyHexMacro(writer_, macroBase + "MASK", 0xFFFFFFFFL);
            writeLengthyHexMacro(writer_, macroBase + "LENGTH", 32);
            writer_.println();
        }
    }


    /* Return a list of all of the Bitfields belonging to given Mode.  Set 'excludeHidden' to True
     * to skip Bitfields that have an XML attribute indicating that they should remain hidden.
     */
    private List<Bitfield> getBitfieldsFromMode(Register owner, Mode mode, boolean excludeHidden) {
        List<Bitfield> bitfieldList = owner.getBitfieldsWithModeID(mode.getID());

        if(excludeHidden) {
            // Walk backwards so that we don't invalid our iterator as we delete nodes.
            for(int i = bitfieldList.size()-1; i >= 0; --i) {
                if(isBitfieldLanguageHidden(bitfieldList.get(i))) {
                    bitfieldList.remove(i);
                }
            }
        }
        
        return bitfieldList;
    }

    /* Return a new list of Nodes that are the base of peripherals; that is, the Node represents an
     * SFR that is the first one of that peripheral and so its address is also the base address of 
     * the peripheral.  We can't use SFR objects directly because the MPLAB X API does not expose
     * the info we need and there isn't a method to get the Node directly from an SFR object.
     */
    private List<Node> getPeripheralBaseNodes(TargetDevice target) {
        ArrayList<Node> baseNodeList = new ArrayList<>(32);
        List<Node> regions = target.getPic().getMainPartition().getSFRRegions();

        for(Node sfrSection : regions) {
            List<Node> children = Utils.filterAllChildNodes(sfrSection, 
                                                            "edc:SFRDef", 
                                                            "ltx:baseofperipheral", 
                                                            null);
            baseNodeList.addAll(children);
        }

        return baseNodeList;
    }

    /* Return True if the given bitfield was not meant to appear in the header file.  These would
     * presumably be used by the MPLAB X simulator or debugger.
     */
    private boolean isBitfieldLanguageHidden(Bitfield bf) {
        // There's multiple attributes to indicate if a field should be hidden, but it appears that
        // the one we want here is not exposed via the MPLAB X API.  Therefore we have check it
        // manually.  We need the "edc:" prefix because we're not using the MPLAB X API.
        return Utils.getNodeAttributeAsBool(bf.getNode(), "edc:islanghidden", false);
    }

    /* Write a macro that corresponds to a register address.  This will write the macro such that
     * the 'addr' value is treated as a pointer to a uint32_t value.  That is, the output will be
     * "(*(uint32_t *)addr)".
     */
    private void writeRegisterAddressMacro(PrintWriter writer, String sfrName, long addr) {
        String addrStr = "0x" + Long.toHexString(makeKseg1Addr(addr)).toUpperCase();

        writeStringMacro(writer,
                         sfrName,
                         "(*(volatile uint32_t *)" + addrStr + ")",
                         null);
    }

    /* Write a macro that allows a register's address to be used as its structure definition indicates.
     * That is, this is like above but the 'addr' value is treated as a pointer to the typedef
     * structure "__sfrNamebits_t".
     */
    private void writeRegisterBitsMacro(PrintWriter writer, String sfrName, long addr) {
        String addrStr = "0x" + Long.toHexString(makeKseg1Addr(addr)).toUpperCase();
        String bitsName = getRegisterBitsName(sfrName);

        writeStringMacro(writer,
                         sfrName + "bits",
                         "(*(volatile " + bitsName + " *)" + addrStr + ")",
                         null);        
    }

    /* Write a macro that evaluates to a hex value.  Use this for macros that have particularly
     * long names because this will add a lot of padding between the macro name and value.
     */
    private void writeLengthyHexMacro(PrintWriter writer, String macroName, long value) {
        String macro = "#define " + macroName;
        macro = Utils.padStringWithSpaces(macro, 56, 4);
        macro += String.format("(0x%08X)", value);

        writer.println(macro);
    }

    /* Write a macro that evaluates to a deciaml value.  Use this for macros that have particularly
     * long names because this will add a lot of padding between the macro name and value.
     */
    private void writeLengthyDecimalMacro(PrintWriter writer, String macroName, long value) {
        String macro = "#define " + macroName;
        macro = Utils.padStringWithSpaces(macro, 56, 4);
        macro += "(" + value + ")";

        writer.println(macro);
    }

    /* Write a macro that is surrounded by a check if the macro has already been defined.  That is,
     * write the macro in the form:
     *
     *    #ifndef macroName
     *    #  define macroName   [value, if present]
     *    #endif
     */
    private void writeGuardedMacro(PrintWriter writer, String macroName, String value) {
        String macro = "#  define " + macroName;

        if(null != value  &&  !value.isEmpty()) {
            macro = Utils.padStringWithSpaces(macro, 40, 4);
            macro += value;
        }

        writer.println("#ifndef " + macroName);
        writer.println(macro);
        writer.println("#endif");
    }

    /* Return the typename of the C struct/union that breaks out the bitfields of the given register.
     * All this actually does is return "__regNamebits_t".
    */
    private String getRegisterBitsName(String regName) {
        return "__" + regName + "bits_t";
    }

    /* Return an address that is in kseg1 space.  The MIPS address space has half of it used for 
     * user space (useg) and the other half is split four ways into the four kernel segments
     * (kseg0-kseg3).
     */
    private long makeKseg1Addr(long addr) {
        return ((addr & 0x1FFFFFFFL) | 0xA0000000L);
    }

    /* Return the substring of the given string starting with 'begin' and ending one before 'end'
     * (like with Java's String.substring() method) and with any leading zeroes in the resulting
     * substring removed.  Presumably, you'd use this when grabbing numbers from strings.
     */
    private String substringWithoutLeadingZeroes(String str, int begin, int end) {
        String substr = str.substring(begin, end);

        int i;
        for(i = 0; i < substr.length()  &&  '0' == substr.charAt(i); ++i) {
        }

        return substr.substring(i);
    }
}
