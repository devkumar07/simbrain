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
package org.simbrain.network.synapse_update_rules;

import org.simbrain.network.core.Synapse;
import org.simbrain.network.core.SynapseUpdateRule;
import org.simbrain.util.UserParameter;

/**
 * <b>HebbianThresholdSynapse</b>.
 */
public class HebbianThresholdRule extends SynapseUpdateRule {

    // TODO: check description
    /**
     * Learning rate.
     */
    @UserParameter(label = "Learning rate", description = "Learning rate for Hebb threshold rule", increment = .1, order = 1)
    private double learningRate;

    /**
     * Output threshold.
     */
    @UserParameter(label = "Threshold", description = "Output threshold for Hebb threshold rule", increment = .1, order = 1)
    private double outputThreshold = .5;

    /**
     * Output threshold momentum.
     */
    @UserParameter(label = "Threshold Momentum", description = "Output threshold momentum for Hebb threshold rule", increment = .1, order = 1)
    private double outputThresholdMomentum = .1;

    /**
     * Use sliding output threshold.
     */
    @UserParameter(label = "Sliding Threshold", description = "Use sliding output threshold for Hebb threshold rule", order = 1)
    private boolean useSlidingOutputThreshold = false;

    @Override
    public void init(Synapse synapse) {
    }

    @Override
    public String getName() {
        return "Hebbian Threshold";
    }

    @Override
    public SynapseUpdateRule deepCopy() {
        HebbianThresholdRule h = new HebbianThresholdRule();
        h.setLearningRate(getLearningRate());
        h.setOutputThreshold(this.getOutputThreshold());
        h.setOutputThresholdMomentum(this.getOutputThresholdMomentum());
        h.setUseSlidingOutputThreshold(this.getUseSlidingOutputThreshold());
        return h;
    }

    @Override
    public void update(Synapse synapse) {
        double input = synapse.getSource().getActivation();
        double output = synapse.getTarget().getActivation();

        if (useSlidingOutputThreshold) {
            outputThreshold += (outputThresholdMomentum * ((output * output) - outputThreshold));
        }
        double strength = synapse.getStrength() + (learningRate * input * output * (output - outputThreshold));
        synapse.setStrength(synapse.clip(strength));
    }

    public double getLearningRate() {
        return learningRate;
    }

    public void setLearningRate(final double momentum) {
        this.learningRate = momentum;
    }

    public double getOutputThreshold() {
        return outputThreshold;
    }

    public void setOutputThreshold(final double outputThreshold) {
        this.outputThreshold = outputThreshold;
    }

    public boolean getUseSlidingOutputThreshold() {
        return useSlidingOutputThreshold;
    }

    public void setUseSlidingOutputThreshold(final boolean useSlidingOutputThreshold) {
        this.useSlidingOutputThreshold = useSlidingOutputThreshold;
    }

    public double getOutputThresholdMomentum() {
        return outputThresholdMomentum;
    }

    public void setOutputThresholdMomentum(final double outputThresholdMomentum) {
        this.outputThresholdMomentum = outputThresholdMomentum;
    }
}
