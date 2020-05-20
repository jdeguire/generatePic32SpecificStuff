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
import java.io.PrintWriter;
import java.util.List;

/**
 * A class used to generate a configuration file for each target device that would be passed to
 * Clang.  These files contain options that one would normally specify on the command line and so
 * it is more convenient to select a config file for a device rather than specifying all of the
 * architecture-specific options manually.  This also means that the MPLAB X plugin has to know less
 * about all of the devices.
 * 
 * Note that not all configuration options can be put here because some depend on what the user
 * selects in MPLAB X, such as optimization options, library options, and ISA options (MIPS32 vs.
 * microMIPS, for example).
 */
public class TargetConfigGenerator {

    private final String configDir_;

    /* Create a new TargetConfigGenerator that can be used to generate target config files for
     * multiple devices.  All config files generated will be put into the base path as ".cfg" files
     * with the filename being the name of the device in lower-case.
     */
    public TargetConfigGenerator(String basepath) {
        configDir_ = basepath;
    }

    public void generate(TargetDevice target) throws java.io.FileNotFoundException {
        String targetPath = configDir_ + "/" + target.getDeviceName().toLowerCase() + ".cfg";

        try(PrintWriter writer = Utils.createUnixPrintWriter(targetPath)) {
            outputLicenseString(writer);
            outputTargetArchOptions(writer, target);
            outputFpuOptions(writer, target);
            outputDspR2Option(writer, target);
            outputSystemIncludeDirOptions(writer, target);
            outputLibraryDirOptions(writer, target);
            outputTargetMacros(writer, target);

            writer.println("# This config file does not include library paths because the");
            writer.println("# specific path chosen depends on other linker options, such as");
            writer.println("# optimization level and whether to use a compressed instruction set.");
            writer.println("# The MPLAB X plugin handles this for you.");

// TODO: Do we need -nostdinc or -nostdlib/-nodefaultlibs/-nolibc?
// TOOD: What about CMSIS directories?
        }
    }


    /* Ouput a license string comment to indicate that this file is reusable under the BSD 3-clause
     * license.
     */
    private void outputLicenseString(PrintWriter writer) {
        String header = (Utils.generatedByString() + "\n\n" + Utils.generatorLicenseString());

        Utils.writeMultilineComment(writer, 0, header, "# ", "# ", "");
        writer.println();
    }

    /* Output basic options for the given device that tell the compiler the device's architecture.
     */
    private void outputTargetArchOptions(PrintWriter writer, TargetDevice target) {
        writer.println("# Base target arch options.");
        writer.println("-target " + target.getTargetTripleName());
        writer.println("-march=" + target.getArchNameForCompiler());
        
        if(target.isArm()) {
            writer.println("-mtune=" + target.getCpuName());
        } else if(target.supportsMicroMipsIsa()  &&  !target.supportsMips32Isa()) {
            writer.println("-mmicromips");
        }

        writer.println();
    }

    /* Output options telling the compiler what FPU the device uses, if any.
     */
    private void outputFpuOptions(PrintWriter writer, TargetDevice target) {
        if(target.hasFpu()) {
            if(target.isMips32()) {
                writer.println("# Device has a 64-bit FPU.");
                writer.println("-mhard-float");
                writer.println("-mfloat-abi=hard");
                writer.println("-mfp64");
            } else if(target.isArm()) {
                writer.println("# Device has an FPU.");
                writer.println("-mfpu=" + target.getArmFpuName());
                writer.println("-mfloat-abi=hard");
            }
        } else {
            writer.println("# Device uses software floating-point.");
            writer.println("-msoft-float");
            writer.println("-mfloat-abi=soft");
        }

        writer.println();
    }

    /* Output an option telling the compiler that the device supports the MIPS DSPr2 extension, if
     * it actually does.
     */
    private void outputDspR2Option(PrintWriter writer, TargetDevice target) {
        if(target.supportsDspR2Ase()) {
            writer.println("# Device supports MIPS DSPr2 ASE.");
            writer.println("-mdspr2");
            writer.println();
        }
    }

    /* Output an option to tell the compiler where to find device-specific headers.
     */
    private void outputSystemIncludeDirOptions(PrintWriter writer, TargetDevice target) {
        String archDir = getArchDirName(target);

        writer.println("# The '=' makes this directory relative to the --sysroot option.");
        writer.println("# When using this config file, the --sysroot option must be provided to");
        writer.println("# point to the base directory of where this toolchain is located.");
        writer.println("# The MPLAB X plugin will do this for you.");
        writer.println("-isystem \"=/target/" + archDir + "/include\"");
        writer.println();

// TODO:  Does Clang use directories relative to this config file?  If so, we might be able to set
//        sysroot based on where this file is located.
    }

    /* Output an option to tell the linker where to find device-specific object files or linker
     * scripts.
     */
    private void outputLibraryDirOptions(PrintWriter writer, TargetDevice target) {
        String archDir = getArchDirName(target);
        String devname = target.getDeviceName().toLowerCase();

        writer.println("-L\"=/target/" + archDir + "/lib/proc/" + devname + "\"");
        writer.println();

// TODO:  Does Clang use directories relative to this config file?  If so, we might be able to set
//        sysroot based on where this file is located.
    }

    /* Output macros that are useful to a particular target.
     */
    private void outputTargetMacros(PrintWriter writer, TargetDevice target) {
        writer.println("# These are macros useful for the target.");
        writer.println("# Some of these are predefined by XC32 and are here for compatibility.");

        writer.println("-D__pic32_clang__");
        writer.println("-D__PIC32");
        writer.println("-D__PIC32__");

        if(target.isMips32()) {
            writer.println("-D__PIC32M");
            writer.println("-D__PIC32M__");

            List<Pair<String, String>> mipsMacros = MipsCommon.getMipsFeatureMacros(target);
            for(Pair<String, String> macro : mipsMacros) {
                if(macro.second != null  &&  !macro.second.isEmpty()) {
                    writer.println("-D" + macro.first + "=" + macro.second);
                } else {
                    writer.println("-D" + macro.first);
                }
            }
        } else {
            writer.println("-D__PIC32C");
            writer.println("-D__PIC32C__");

            String devmacroname = target.getDeviceNameForMacro();
            writer.println("-D__" + devmacroname);
            writer.println("-D__" + devmacroname + "__");

            String series = target.getDeviceSeriesName();
            writer.println("-D__" + series);
            writer.println("-D__" + series + "__");

            // These next macros are probably not in XC32, but might be handy for cross-arch code.
            //
            if(target.hasL1Cache()) {
                writer.println("-D__PIC32_HAS_L1CACHE");
            }

            if(target.hasFpu()) {
                writer.println("-D__PIC32_HAS_FPU32");

                if(target.hasFpu64()) {
                    writer.println("-D__PIC32_HAS_FPU64");
                }
            }
        }
        
        writer.println();
    }


    /* Return a name that can be used for an architecture-specific directory.
     */
    private String getArchDirName(TargetDevice target) {
        if(target.isMips32())
            return "mips32";
        else {
            // This should get us "cortex-m" or "cortex-a" or whatever else.
            return target.getCpuName().toLowerCase().substring(0, 8);
        }
    }
}
