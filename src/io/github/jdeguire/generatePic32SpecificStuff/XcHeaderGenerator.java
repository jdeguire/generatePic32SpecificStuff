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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;

/**
 * This is a class to generate the "xc.h" header files that users can include to automatically get
 * the correct headers for their device.  This is generated for compatibility with Microchip's XC32
 * compiler.
 */
public class XcHeaderGenerator {
    /* This is a utility class to let us track just the info we need instead of whole TargetDevice
     * instances for each device.  This should help save memory usage.
     */
    private class DeviceInfo {
        public String devname;
        public String macroname;
    };

    private final String baseXcPath_;
    private final String relativeHeaderPath_;
    private final String relativeLegacyHeaderPath_;
    private final LinkedHashMap<String, ArrayList<DeviceInfo>> deviceMap_;

    /* Create a new generator object with the given paths, which should all be directories.  The
     * first paramter is where to put the "xc.h" header itself.  The second parameter is the location
     * of the device-specific header files.  The third parameter is where legacy headers would be
     * located if present; this can be null or empty to indicate that there are no legacy headers.
     * This class assumes that the "xc.h" file would be at some top-level path and that the device
     * headers would be in subdirectories.
     */
    public XcHeaderGenerator(String baseXcPath, String baseHeaderPath, String baseLegacyHeaderPath) {
        baseXcPath_ = baseXcPath;
        relativeHeaderPath_ = Utils.getRelativePath(baseXcPath, baseHeaderPath);

        if(null != baseLegacyHeaderPath  &&  !baseLegacyHeaderPath.isEmpty()) {
            relativeLegacyHeaderPath_ = Utils.getRelativePath(baseXcPath, baseLegacyHeaderPath);
        } else {
            relativeLegacyHeaderPath_ = "";
        }

        deviceMap_ = new LinkedHashMap<>();
    }

    /* Reset this instance's internal data to prepare to receive a new set of target devices for XC
     * file generation.
     */
    public void reset() {
        deviceMap_.clear();
    }

    /* Add the given device to be included in the XC header file generation when generate() is called
     * later.
     */
    public void add(TargetDevice target) {
        String series = target.getDeviceSeriesName();

        if(!deviceMap_.containsKey(series)) {
            deviceMap_.put(series, new ArrayList<DeviceInfo>());
        }

        DeviceInfo devinfo = new DeviceInfo();
        devinfo.devname = target.getDeviceName();
        devinfo.macroname = target.getDeviceNameForMacro();

        deviceMap_.get(series).add(devinfo);
    }

    /* Generate the "xc.h" file using the paths given to the constructor and the devices that have
     * been added so far with the addDevice() method.
     */
    public void generate() throws java.io.FileNotFoundException {
        try(PrintWriter writer = Utils.createUnixPrintWriter(baseXcPath_ + "/xc.h")) {
            Iterator<String> seriesIterator = deviceMap_.keySet().iterator();

            outputLicenseHeader(writer);
            outputPreamble(writer);

            boolean firstSeries = true;

            while(seriesIterator.hasNext()) {
                String series = seriesIterator.next();
                ArrayList<DeviceInfo> infoList = deviceMap_.get(series);

                if(firstSeries) {
                    writer.println("#if defined(__" + series + "__)");
                    firstSeries = false;
                } else {
                    writer.println("#elif defined(__" + series + "__)");                    
                }

                outputDevicesInSeries(writer, series, infoList);
            }

            writer.println("#else");
            writer.println("#  error Unrecognized device series");
            writer.println("#endif /* series checks */");
            writer.println();

            outputConclusion(writer);
        }
    }

    /* Output the incluce directives for all of the devices in the given list with the assumption
     * that they are part of the given series.
     */
    private void outputDevicesInSeries(PrintWriter writer, String series, List<DeviceInfo> infoList) {
        boolean firstDevice = true;
        boolean addLegacyDirective = !relativeLegacyHeaderPath_.isEmpty();
        
        for(DeviceInfo devinfo : infoList) {
            if(firstDevice) {
                writer.println("#  if defined(__" + devinfo.macroname.toUpperCase() + "__)");
                firstDevice = false;
            } else {
                writer.println("#  elif defined(__" + devinfo.macroname.toUpperCase() + "__)");
            }

            String filename = devinfo.devname.toLowerCase() + ".h";

            if(addLegacyDirective) {
                writer.println("#    ifdef USE_LEGACY_DEVICE_HEADERS");
                writer.println("#      include \"" + relativeLegacyHeaderPath_ + "/" + filename + "\"");
                writer.println("#    else");
                writer.println("#      include \"" + relativeHeaderPath_ + "/" + filename + "\"");
                writer.println("#    endif");
            } else {
                writer.println("#    include \"" + relativeHeaderPath_ + "/" + filename + "\"");
            }
        }

        writer.println("#  else");
        writer.println("#    error Unrecognized " + series + " device");
        writer.println("#  endif /* " + series + " */");
    }

    /* Add the license header to the "xc.h" header file opened by the given writer.  This will 
     * output the license for this generator and Microchip's BSD license.
     */
    private void outputLicenseHeader(PrintWriter writer) {
        String header = (Utils.generatedByString() + "\n\n" +
                         Utils.generatorLicenseString() + "\n\n" +
                         "                                               ******\n\n" + 
                         "This file is generated based on source files included with Microchip " +
                         "Technology's XC32 toolchain.  Microchip's license is reproduced below:\n\n" +
                         Utils.microchipBsdLicenseString());

        Utils.writeMultilineCComment(writer, 0, header);
        writer.println();
    }

    private void outputPreamble(PrintWriter writer) {
        writer.println("#ifndef __XC_H__");
        writer.println("#define __XC_H__");
        writer.println();
    }

    private void outputConclusion(PrintWriter writer) {
        writer.println("/* Extra arch-specific macros and functions. */");
        writer.println("#include <arch_extra.h>");
        writer.println();
        writer.println("#endif /* __XC_H__ */");
        writer.println();
    }
}
