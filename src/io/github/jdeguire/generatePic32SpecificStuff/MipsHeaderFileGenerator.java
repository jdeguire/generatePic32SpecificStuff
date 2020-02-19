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
import com.microchip.crownking.edc.Option;
import com.microchip.crownking.edc.Register;
import com.microchip.crownking.edc.SFR;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Node;

/**
 * A subclass of the HeaderFileBuilder class that handles MIPS devices.
 */
public class MipsHeaderFileGenerator extends HeaderFileGenerator {

    public MipsHeaderFileGenerator(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) throws java.io.FileNotFoundException {
        List<SFR> sfrList = target.getSFRs();
        List<DCR> dcrList = target.getDCRs();

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
        outputInterruptMacros(target);
        outputPeripheralMacros(target);
        outputMemoryRegionMacros(target);
        outputConfigRegisterMacros(dcrList);
        outputTargetFeatureMacros(target);

        outputIncludeGuardEnd(target);
        closeHeaderFile();
    }


    /* Output the opening "#ifndef...#define" sequence of an include guard for this header file.
     */
    private void outputIncludeGuardStart(TargetDevice target) {
        writer_.println("#ifndef __" + target.getDeviceNameForMacro() + "_H");
        writer_.println("#define __" + target.getDeviceNameForMacro() + "_H");
        writer_.println();
    }

    /* Output the closing "#endif" of an include guard for this header file.
     */
    private void outputIncludeGuardEnd(TargetDevice target) {
        writer_.println("#endif  /* __" + target.getDeviceNameForMacro() + "_H */");
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
    private void outputInterruptMacros(TargetDevice target) {
        InterruptList interruptList = target.getInterruptList();
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
    private void outputMemoryRegionMacros(TargetDevice target) {
        ArrayList<LinkerMemoryRegion> lmrList = new ArrayList<>();
        MipsCommon.addStandardMemoryRegions(lmrList, target, Collections.<DCR>emptyList());

        writer_.println("/* Default Memory Region Macros */");
        for(LinkerMemoryRegion lmr : lmrList) {
            String lmrMacroBase = "__" + lmr.getName().toUpperCase() + "_";

            writeLengthyHexMacro(writer_, lmrMacroBase + "BASE", lmr.getStartAddress());
            writeLengthyHexMacro(writer_, lmrMacroBase + "LENGTH", lmr.getLength());
        }

        writer_.println();
    }

    /* Output macros that can be used to configure the PIC32's configuration registers, which are
     * just special locations in flash memory that the device reads to determine things like clock
     * settings and Ethernet pins.  XC32 has special config pragmas to do this and Clang obviously
     * doesn't, so we have to come up with some other way.
    */
    private void outputConfigRegisterMacros(List<DCR> dcrList) {
        Utils.writeMultilineCComment(writer_, 0, 
                "Use the following macros to set the configuration registers on the device.\n"
                        + "To do this, AND together the desired options to fill out the fields of "
                        + "that particular register, like so:\n\n"
                        + "  __setDEVCFG1(__DEVCFG1_FNOSC_PRIPLL & __DEVCFG1_FPBDIV_DIV_1);\n\n"
                        + "Do this for the config registers you want to configure by using the "
                        + "macros somewhere in a C or C++ source file.");
        writer_.println();

        for(DCR dcr : dcrList) {
            writer_.println("/*******************");
            writer_.println(" * " + dcr.getName());
            writer_.println(" */");

            String dcrName = dcr.getName();
            String dcrMemSection = "." + MipsCommon.getDcrMemorySectionName(dcr);
            String dcrAttribs = "__attribute__((unused, section(\"" + dcrMemSection + "\")))";
            String dcrSetMacroName = "__set" + dcrName + "(f)";
            String dcrSetMacroValue = "const volatile uint32_t " + dcrAttribs + " __" + dcrName + " = (f)";
            String dcrDefaultMacroName = "__" + dcrName + "_default";
            String dcrDefaultMacroValue = String.format("(0x%08X)", dcr.getDefault());
            String dcrDefaultMacroDesc = "Default value for " + dcrName;

            writeStringMacro(writer_, dcrSetMacroName, dcrSetMacroValue, null);
            writeStringMacro(writer_, dcrDefaultMacroName, dcrDefaultMacroValue, dcrDefaultMacroDesc);
            writer_.println();

            List<Bitfield> bitfieldList = dcr.getBitfields();
            for(Bitfield bitfield : bitfieldList) {
                String bfName = bitfield.getName();
                String bfDesc = bitfield.getDesc();
                writer_.println("// " + dcrName + "<" + bfName + ">:  " + bfDesc);

                long bfPosition = bitfield.getPlace();
                long bfMask = bitfield.getMask() << bfPosition;

                List<Option> optionList = bitfield.getOptions();
                for(Option option : optionList) {
                    String optName = option.getName();
                    String optDesc = option.getDesc();
                    long optMask = Register.parseWhen2(option.get("when")).second << bfPosition;

                    writeStringMacro(writer_, 
                                     "__" + dcrName + "_" + bfName + "_" + optName,
                                     String.format("(0x%08X)", (0xFFFFFFFFL & ~bfMask | optMask)),
                                     optDesc);
                }

                writer_.println();
            }
        }
    }

    /* Output macros that code can use to query device feature, such as instruction set support or
     * the number of shadow registers.  This function needs to know the number of shadow register 
     * sets the device has, which can be retrieved from an InterruptList.
     */
    private void outputTargetFeatureMacros(TargetDevice target) {
        Utils.writeMultilineCComment(writer_, 0, 
                "These macros should already be provided if you used one of the target config "
                        + "files bundled with Clang (see the /target/config directory).  The "
                        + "MPLAB X plugin will handle this for you if you didn't decide to use "
                        + "your own.\n\n"
                        + "These are here just to help MPLAB X find what macros are defined.");

        // Notice that we are not outputting XC32 macros (like "__XC32__", "__XC32_VERSION__", etc.)
        // because the MPLAB X plugin will let the user select if those macros should be included
        // and will let the user fake an XC32 version.
        List<Pair<String, String>> macroList = MipsCommon.getMipsFeatureMacros(target);
        for(Pair<String, String> macro : macroList) {
            writeGuardedMacro(writer_, macro.first, macro.second);
        }
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
}
