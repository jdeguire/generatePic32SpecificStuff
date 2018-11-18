/* Copyright (c) 2018, Jesse DeGuire
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

import java.util.List;
import javax.swing.SwingWorker;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//com.github.jdeguire.generatePic32SpecificStuff//MainWindow//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "MainWindowTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "rightSlidingSide", openAtStartup = false)
@ActionID(category = "Window", id = "com.github.jdeguire.generatePic32SpecificStuff.MainWindowTopComponent")
@ActionReference(path = "Menu/Tools/Embedded" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_MainWindowAction",
        preferredID = "MainWindowTopComponent"
)
@Messages({
    "CTL_MainWindowAction=MainWindow",
    "CTL_MainWindowTopComponent=MainWindow Window",
    "HINT_MainWindowTopComponent=This is a MainWindow window"
})

public final class MainWindowTopComponent extends TopComponent {

    InputOutput io;
    StuffGenerator worker;
    
    public MainWindowTopComponent() {
        initComponents();
        setName(Bundle.CTL_MainWindowTopComponent());
        setToolTipText(Bundle.HINT_MainWindowTopComponent());

        io = IOProvider.getDefault().getIO ("Hello", true);
        worker = new StuffGenerator();
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT
     * modify this code. The content of this method is always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jButton1 = new javax.swing.JButton();

        org.openide.awt.Mnemonics.setLocalizedText(jButton1, org.openide.util.NbBundle.getMessage(MainWindowTopComponent.class, "MainWindowTopComponent.jButton1.text")); // NOI18N
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(150, 150, 150)
                .addComponent(jButton1)
                .addContainerGap(177, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(125, 125, 125)
                .addComponent(jButton1)
                .addContainerGap(152, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        worker.execute();
    }//GEN-LAST:event_jButton1ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
        // TODO add custom code on component opening
    }

    @Override
    public void componentClosed() {
        // TODO add custom code on component closing
        io.getOut().close();
        io.getErr().close();
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
    private class StuffGenerator extends SwingWorker<Void, Void> {

        /* This runs in a background worker thread, so outer class members and the UI should not be
         * updated here.  The return value will be made available to other threads via the get()
         * method when this returns or interim results can be output usin the publish method().
         */
        @Override
        public Void doInBackground() {
            // Check if the outer class wants to cancel (ie. it called cancel()
            if(!isCancelled()) {
                // These are synchronized internally, so we should be okay calling them here.
                io.getOut().println ("Hello from standard out");
                io.getErr().println ("Hello from standard err");  //this text should appear in red
            }

            return null;          // A Void object cannot be returned directly.
        }
        
        /* This runs in the Event Dispatch Thread (EDT), which is used for updating the UI.  This is
         * executed when doInBackground() is done.  If doInBackground() returned anything, you'd use
         * get() to retrieve it and update the UI and the outer class's members in here.
         */
        @Override
        public void done() {

        }

        /* This runs in the EDT and takes values provided by doInBackground() using the publish()
         * method.  This lets us update the UI as the worker is running.
         */
        @Override
        public void process(List<Void> chunks) {
            
        }
    }    
}
