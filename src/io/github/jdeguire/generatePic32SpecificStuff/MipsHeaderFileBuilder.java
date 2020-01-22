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

import com.microchip.crownking.edc.Mode;
import com.microchip.crownking.edc.SFR;
import java.io.PrintWriter;
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
        createNewHeaderFile(target);

        outputLicenseHeader(writer_, false);
        outputIncludeGuardStart(getDeviceNameForHeader(target).substring(1));  // remove starting 'P'
        writeNoAssemblyStart(writer_);
        outputIncludedHeaders();
        outputExternCStart();
        outputSfrDefinitions(target);
// TODO:  We also need to output DCR definitions.
        outputExternCEnd();

        writer_.println("#else  /* __ASSEMBLER__ */");
        writer_.println();

        writeNoAssemblyEnd(writer_);
        writer_.println();

        outputIncludeGuardEnd(getDeviceNameForHeader(target).substring(1));
        closeHeaderFile();
    }


    /* Output the opening "#ifndef...#define" sequence of an include guard for this header file.
     */
    private void outputIncludeGuardStart(String devname) {
        devname = devname.toUpperCase();
        writer_.println("#ifndef __" + devname + "_H");
        writer_.println("#define __" + devname + "_H");
        writer_.println();
    }

    /* Output the closing "#endif" of an include guard for this header file.
     */
    private void outputIncludeGuardEnd(String devname) {
        devname = devname.toUpperCase();
        writer_.println("#endif  /* __" + devname + "_H */");
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
    private void outputSfrDefinitions(TargetDevice target) {
        List<SFR> sfrList = target.getSFRs();

        for(SFR sfr : sfrList) {
            String sfrName = sfr.getName();
            long sfrAddr = target.getRegisterAddress(sfr);
            List<Node> modeList = sfr.getModes();

            writeRegisterAddressMacro(writer_, sfrName, sfrAddr);

            if(modeList.size() > 1) {
// TODO:  Need to add function to output mode unions.
                writeRegisterBitsMacro(writer_, sfrName, sfrAddr);
            }

            // These are the CLR, SET, and INV registers that most SFRs on the PIC32 have.
            if(sfr.hasPortals()) {
                List<String> portalNames = sfr.getPortalNames();
                long portalAddr = sfrAddr;
                
                for(String portal : portalNames) {
                    portalAddr += 4;
                    writeRegisterAddressMacro(writer_, sfrName + portal, portalAddr);
                }
            }

            writer_.println();
        }

        writer_.println();
    }

    /* Return an address that is in kseg1 space.  The MIPS address space has half of it used for 
     * user space (useg) and the other half is split four ways into the four kernel segments
     * (kseg0-kseg3).
     */
    private long makeKseg1Addr(long addr) {
        return ((addr & 0x1FFFFFFFL) | 0xA0000000L);
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

    /* Write a macro that allows a register's addres to be used as its structure definition indicates.
     * That is, this is like above but the 'addr' value is treated as a pointer to the typedef
     * structure "__sfrNamebits_t".
     */
    private void writeRegisterBitsMacro(PrintWriter writer, String sfrName, long addr) {
        String addrStr = "0x" + Long.toHexString(makeKseg1Addr(addr)).toUpperCase();

        writeStringMacro(writer,
                         sfrName + "bits",
                         "(*(volatile __" + sfrName + "bits_t *)" + addrStr + ")",
                         null);        
    }
}
