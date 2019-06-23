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

import java.util.List;
import javax.swing.SwingWorker;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;

import com.microchip.crownking.mplabinfo.DeviceSupport.Device;
import java.util.ArrayList;
import javax.swing.JFileChooser;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//io.github.jdeguire.generatePic32SpecificStuff//MainWindow//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "MainWindowTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "rightSlidingSide", openAtStartup = false)
@ActionID(category = "Window", id = "io.github.jdeguire.generatePic32SpecificStuff.MainWindowTopComponent")
@ActionReference(path = "Menu/Tools/Embedded" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_MainWindowAction",
        preferredID = "MainWindowTopComponent"
)
@Messages({
    "CTL_MainWindowAction=Generate PIC32 Stuff",
    "CTL_MainWindowTopComponent=Stuff Generator",
    "HINT_MainWindowTopComponent=This is a MainWindow window"
})

public final class MainWindowTopComponent extends TopComponent {

    StuffGeneratorWorker worker = null;
    JFileChooser filechooser;
    boolean working = false;
    
    public MainWindowTopComponent() {
        initComponents();
        setName(Bundle.CTL_MainWindowTopComponent());
        setToolTipText(Bundle.HINT_MainWindowTopComponent());

        filechooser = new JFileChooser();
        filechooser.setDialogTitle("Select output directory");
        filechooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        filechooser.setAcceptAllFileFilterUsed(false);
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT
     * modify this code. The content of this method is always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        OutputLog = new javax.swing.JTextArea();
        StartButton = new javax.swing.JButton();

        OutputLog.setEditable(false);
        OutputLog.setColumns(20);
        OutputLog.setRows(5);
        jScrollPane1.setViewportView(OutputLog);

        org.openide.awt.Mnemonics.setLocalizedText(StartButton, org.openide.util.NbBundle.getMessage(MainWindowTopComponent.class, "MainWindowTopComponent.StartButton.text")); // NOI18N
        StartButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                StartButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 292, Short.MAX_VALUE)
                    .addComponent(StartButton))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(StartButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 319, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void StartButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_StartButtonActionPerformed
        // Workers run only once, no matter how many times the button is clicked.  We'll set a
        // flag and create a new worker so that we can run multiple times, but also prevent the 
        // button from doing anything if a worker is already working.
        if(!working) {
            if(filechooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                working = true;

                String outputpath = filechooser.getSelectedFile().toString();
                OutputLog.setText("Outputting to " + outputpath);

                worker = new StuffGeneratorWorker(outputpath);            
                worker.execute();
            }
        }
    }//GEN-LAST:event_StartButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextArea OutputLog;
    private javax.swing.JButton StartButton;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
        // TODO add custom code on component opening
    }

    @Override
    public void componentClosed() {
        // TODO add custom code on component closing
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }
    
    
    /* This inner class actually does the work of generating the device-specific stuff.
     * I mainly followed the "Concurrency in Swing" tutorial at: 
     * https://docs.oracle.com/javase/tutorial/uiswing/concurrency/index.html
     *
     * The first type parameter is the return type of doInBackground() and get().  The second is
     * the type for interim results that doInBackground() can output using the publish()
     * method to the process() method.
     */
    private class StuffGeneratorWorker extends SwingWorker<Void, String> {

        private final String outputpath;

        public StuffGeneratorWorker(String outputpath) {
            super();
            this.outputpath = outputpath;
        }

        /* This runs in a background worker thread, so outer class members and the UI should not be
         * updated here.  The return value will be made available to other threads via the get()
         * method when this returns or interim results can be output using the publish() method.
         */
        @Override
        public Void doInBackground() {
            StuffGenerator gen = new StuffGenerator(outputpath);

            try {
                List<Device> deviceList = gen.getDeviceList();
                for(Device device : deviceList) {
/*                    Family family = device.getFamily();

                    if(Family.ARM32BIT == family) {
                        publish(device.getName() + " (ARM32)");
                        gen.generate(device);
                    }
                    else if(Family.PIC32 == family) {
                        publish(device.getName() + " (MIPS32)");
                        gen.generate(device);
                    }
*/
                    // DEBUG STUFF FOR NOW
                    if("PIC32MX795F512L".equalsIgnoreCase(device.getName())  ||  
                       "ATSAME54P20A".equalsIgnoreCase(device.getName()) ||
                       "ATSAME70Q21B".equalsIgnoreCase(device.getName()) ||
                       "PIC32MZ2048EFH144".equalsIgnoreCase(device.getName())) {
//                        List<String> nodeNames = gen.makeNodeMap(device);
//                        List<String> nodeNames = gen.getMemoryRegionsForLinker(device);
//                        List<String> nodeNames = gen.getMemorySpaces(device);
//                        List<String> nodeNames = gen.getConfigRegAddresses(device);
                        List<String> nodeNames = gen.getAtdfInfo(device);

//                        gen.generate(device);
                        publish("----------" + System.lineSeparator() + device.getName());
                        
                        if(nodeNames.isEmpty()) {
                            publish("Nothing here");
                        }
                        else {
                            for(String name : nodeNames)
                                publish(name);
                        }
                    }
                }
            } catch(Exception ex) {
                publish(ex.getMessage());
            }

            return null;
        }
        
        /* This runs in the Event Dispatch Thread (EDT), which is used for updating the UI.  This is
         * executed when doInBackground() is done.  If doInBackground() returned anything, you'd use
         * get() to retrieve it and update the UI and the outer class's members in here.
         */
        @Override
        public void done() {
            working = false;
        }

        /* This runs in the EDT and takes values provided by doInBackground() using the publish()
         * method.  This lets us update the UI as the worker is running.
         */
        @Override
        public void process(List<String> chunks) {
            for(String chunk : chunks) {
                OutputLog.append(chunk + System.lineSeparator());
            }
        }
    }    
}
