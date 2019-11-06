/*
 * Part of Simbrain--a java-based neural network kit
 * Copyright (C) 2005,2007 The Authors.  See http://www.simbrain.net/credits
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.simbrain.network.gui.nodes;

import org.piccolo2d.PNode;
import org.piccolo2d.nodes.PPath;
import org.simbrain.network.groups.Subnetwork;
import org.simbrain.network.gui.NetworkPanel;
import org.simbrain.network.gui.dialogs.TestInputPanel;
import org.simbrain.network.gui.dialogs.network.SubnetworkPanel;
import org.simbrain.network.trainers.Trainable;
import org.simbrain.util.ResourceManager;
import org.simbrain.util.StandardDialog;
import org.simbrain.util.piccolo.Outline;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PNode representation of a subnetwork. This class contains an interaction box
 * an {@link Outline} node (containing neuron groups and synapse groups)
 * as children. The outlinedobjects node draws the boundary around the contained
 * nodes. The interaction box is the point of contact. Layout happens in the
 * overridden layoutchildren method.
 *
 * @author Jeff Yoshimi
 */
public class SubnetworkNode extends PPath.Float implements GroupNode, PropertyChangeListener {

    /**
     * Parent network panel.
     */
    private final NetworkPanel networkPanel;

    /**
     * Reference to the subnet being represented.
     */
    private final Subnetwork subnetwork;

    /**
     * The interaction box for this neuron group.
     */
    private SubnetworkNodeInteractionBox interactionBox;

    /**
     * The outlined objects (neuron and synapse groups) for this node.
     */
    private final Outline outline = new Outline();

    /**
     * The outlined objects
     */
    private List<PNode> outlinedObjects = new ArrayList<>();

    /**
     * Create a subnetwork node.
     *
     * @param networkPanel parent panel
     * @param group        the layered network
     */
    public SubnetworkNode(NetworkPanel networkPanel, Subnetwork group) {
        this.networkPanel = networkPanel;
        this.subnetwork = group;
        interactionBox = new SubnetworkNodeInteractionBox(networkPanel);
        interactionBox.setText(group.getLabel());
        addChild(outline);
        addChild(interactionBox);
        // Must do this after it's added to properly locate the text
        interactionBox.updateText();

        setContextMenu(this.getDefaultContextMenu());

        addPropertyChangeListener(PROPERTY_FULL_BOUNDS, this);
        group.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("delete".equals(evt.getPropertyName())) {
                    SubnetworkNode.this.removeFromParent();
                } else if ("label".equals(evt.getPropertyName())) {
                    SubnetworkNode.this.updateText();
                }
            }
        });
    }

    /**
     * Override pnode layoutChildren to get objects placed in correct spots.
     */
    @Override
    public void layoutChildren() {
        interactionBox.setOffset(outline.getFullBounds().getX()
                + Outline.ARC_SIZE / 2,
                outline.getFullBounds().getY() - interactionBox.getFullBounds().getHeight() + 1);
    }

    /**
     * Need to maintain a list of nodes which are outlined
     */
    public void addNode(PNode node) {
        outlinedObjects.add(node);
        if (node instanceof SynapseGroupNode) {
            node.lowerToBottom();
        }
    }

    /**
     * Set a custom interaction box.
     *
     * @param newBox the newBox to set.
     */
    protected void setInteractionBox(SubnetworkNodeInteractionBox newBox) {
        this.removeChild(interactionBox);
        this.interactionBox = newBox;
        this.addChild(interactionBox);
        updateText();
    }

    /**
     * Update the text in the interaction box.
     */
    public void updateText() {
        interactionBox.setText(subnetwork.getLabel());
        interactionBox.updateText();
    }

    /**
     * @return the networkPanel
     */
    public NetworkPanel getNetworkPanel() {
        return networkPanel;
    }

    /**
     * Get reference to model subnetwork.
     *
     * @return the subnetwork represented here.
     */
    public Subnetwork getSubnetwork() {
        return subnetwork;
    }

    public Outline getOutline() {
        return outline;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        outline.update(outlinedObjects);
        updateSynapseNodePositions();
    }

    /**
     * Call update synapse node positions on all constituent neuron group nodes,
     * which does the same on all constituent neuron nodes. Ensures synapse
     * nodes are updated properly when this is moved.
     */
    public void updateSynapseNodePositions() {
        for (Object node : outline.getChildrenReference()) {
            if (node instanceof NeuronGroupNode) {
                ((NeuronGroupNode) node).updateSynapseNodePositions();
            }
        }
    }

    /**
     * Basic interaction box for subnetwork nodes. Ensures a property dialog
     * appears when the box is double-clicked.
     */
    public class SubnetworkNodeInteractionBox extends InteractionBox {

        public SubnetworkNodeInteractionBox(NetworkPanel net) {
            super(net);
        }

        @Override
        protected JDialog getPropertyDialog() {
            return SubnetworkNode.this.getPropertyDialog();
        }

        @Override
        public SubnetworkNode getNode() {
            return SubnetworkNode.this;
        }

        @Override
        public Subnetwork getModel() {
            return SubnetworkNode.this.getSubnetwork();
        }

        @Override
        protected boolean hasPropertyDialog() {
            return true;
        }

    }

    /**
     * Helper class to create the subnetwork dialog. Subclasses override this
     * class to create custom property dialogs.
     *
     * @return the neuron group property dialog.
     */
    protected StandardDialog getPropertyDialog() {

        StandardDialog dialog = new StandardDialog() {
            private final SubnetworkPanel panel;

            {
                panel = new SubnetworkPanel(getNetworkPanel(), (Subnetwork) SubnetworkNode.this.getSubnetwork(), this);
                setContentPane(panel);
            }

            @Override
            protected void closeDialogOk() {
                super.closeDialogOk();
            }
        };
        return dialog;
    }


    /**
     * Set a custom context menu for the interaction box.
     *
     * @param menu the menu to set
     */
    public void setContextMenu(final JPopupMenu menu) {
        interactionBox.setContextMenu(menu);
    }

    /**
     * Creates default actions for all model group nodes.
     *
     * @return context menu populated with default actions.
     */
    protected JPopupMenu getDefaultContextMenu() {
        JPopupMenu ret = new JPopupMenu();

        ret.add(renameAction);
        ret.add(removeAction);
        if (subnetwork instanceof Trainable) {
            ret.addSeparator();
            ret.add(testInputAction);
        }
        return ret;
    }

    /**
     * Action for invoking the default edit and properties menu.
     */
    protected Action editAction = new AbstractAction("Edit...") {
        public void actionPerformed(final ActionEvent event) {
            StandardDialog dialog = getPropertyDialog();
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        }
    };

    /**
     * Action for editing the group name.
     */
    protected Action renameAction = new AbstractAction("Rename...") {
        public void actionPerformed(final ActionEvent event) {
            String newName = JOptionPane.showInputDialog("Name:", subnetwork.getLabel());
            subnetwork.setLabel(newName);
        }
    };

    /**
     * Action for removing this group
     */
    protected Action removeAction = new AbstractAction() {

        {
            putValue(SMALL_ICON, ResourceManager.getImageIcon("RedX_small.png"));
            putValue(NAME, "Remove Network...");
            putValue(SHORT_DESCRIPTION, "Remove this subnetwork...");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            getNetworkPanel().getNetwork().removeGroup(subnetwork);
        }
    };

    /**
     * Action for adding the current pattern in the network to the training data
     */
    protected Action addInputRowAction = new AbstractAction() {

        {
            putValue(NAME, "Add current pattern to input data...");
            putValue(SHORT_DESCRIPTION, "Add current pattern to input data...");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            subnetwork.addRowToTrainingSet();
        }
    };

    /**
     * Action for testing inputs to trainable networks.
     */
    private Action testInputAction = new AbstractAction() {

        {
            putValue(SMALL_ICON, ResourceManager.getImageIcon("TestInput.png"));
            putValue(NAME, "Test network...");
            putValue(SHORT_DESCRIPTION, "Test network...");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            if (subnetwork instanceof Trainable) {
                TestInputPanel testInputPanel = TestInputPanel.createTestInputPanel(getNetworkPanel(), ((Trainable) subnetwork).getInputNeurons(), ((Trainable) subnetwork).getTrainingSet().getInputDataMatrix());
                getNetworkPanel().displayPanel(testInputPanel, "Test inputs");

            }
        }
    };

    @Override
    public void updateConstituentNodes() {
        for (Object object : outline.getChildrenReference()) {
            if (object instanceof GroupNode) {
                ((GroupNode) object).updateConstituentNodes();
            }
        }
        //        for (SynapseGroup sg : this.getSubnetwork().getSynapseGroupList()) {
        //            if (sg.isDisplaySynapses()) {
        //                SynapseGroupNodeFull sgf = (SynapseGroupNodeFull) networkPanel
        //                        .getObjectNodeMap().get(sg);
        //                sgf.updateConstituentNodes();
        //            }
        //        }
    }

    @Override
    public void offset(double dx, double dy) {
        for (PNode node : outlinedObjects) {
            if (node instanceof NeuronGroupNode) {
                node.offset(dx, dy);
            }
        }
        outline.update(outlinedObjects);
    }

    @Override
    public List<InteractionBox> getInteractionBoxes() {
        return Collections.singletonList((InteractionBox) interactionBox);
    }

}
