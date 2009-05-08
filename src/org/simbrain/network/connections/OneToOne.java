package org.simbrain.network.connections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import org.simbrain.network.interfaces.Network;
import org.simbrain.network.interfaces.Neuron;
import org.simbrain.network.interfaces.Synapse;
import org.simbrain.network.synapses.ClampedSynapse;

/**
 * Connect each source neuron to a single target.
 * 
 * @author jyoshimi
 */
public class OneToOne extends ConnectNeurons {

    /**
     * The synapse to be used as a basis for the connection. Default to a
     * clamped synapse.
     */
    private Synapse baseSynapse = new ClampedSynapse(null, null);
    
    /**
     * If true, synapses are added in both directions.
     */
    private boolean useBidirectionalConnections = false;

    /** Orientation of how to connect neurons. */
    private Comparator<Neuron> connectOrientation = Y_ORDER;
    
    /**
     * Comparator which orders by X coordinate.
     */
    static final Comparator<Neuron> X_ORDER = new Comparator<Neuron>() {
        public String toString() {
            return "X_ORDER";
        }
        public int compare(final Neuron neuron1, final Neuron neuron2) {
            return Double.compare(neuron1.getX(), neuron2.getX());
        }
    };

    /**
     * Comparator which orders by X coordinate.
     */
    static final Comparator<Neuron> Y_ORDER = new Comparator<Neuron>() {
        public String toString() {
            return "Y_ORDER";
        }
        public int compare(final Neuron neuron1, final Neuron neuron2) {
            return Double.compare(neuron1.getY(), neuron2.getY());
        }
    };
    
    public static Comparator[] getOrientationTypes() {
        return new Comparator[] {X_ORDER, Y_ORDER};
    }


    /**
     * {@inheritDoc}
     */
    public OneToOne(final Network network, final ArrayList<Neuron> neurons,
            final ArrayList<Neuron> neurons2) {
        super(network, neurons, neurons2);
    }

    /** {@inheritDoc} */
    public OneToOne() {
    }

    @Override
    public String toString() {
        return "One to one";
    }

    /**
     * Returns a sorted list of neurons, given a comparator.
     *
     * @param neuronList the base list of neurons.
     * @param comparator the comparator.
     * @return the sorted list.
     */
    private ArrayList<Neuron> getSortedNeuronList(final ArrayList<Neuron> neuronList,
            final Comparator<Neuron> comparator) {
        ArrayList<Neuron> list = new ArrayList<Neuron>();
        list.addAll(neuronList);
        Collections.sort(list, comparator);
        return list;
    }

    /** @inheritDoc */
    public void connectNeurons() {
        
        //TODO: Flags for which comparator to use, including no comparator
        //          (Some users might want random but 1-1 couplings)
        
        Iterator<Neuron> targetsX = getSortedNeuronList(targetNeurons,
                connectOrientation).iterator();

        for (Iterator<Neuron> sources = getSortedNeuronList(sourceNeurons,
                connectOrientation).iterator(); sources.hasNext(); ) {
            Neuron source = (Neuron) sources.next();
            if (targetsX.hasNext()) {
                Neuron target = (Neuron) targetsX.next();
                Synapse synapse = baseSynapse.duplicate();
                synapse.setSource(source);
                synapse.setTarget(target);
                network.addSynapse(synapse);
                // Allow neurons to be connected back to source.
                if (useBidirectionalConnections) {
                    Synapse synapse2 = baseSynapse.duplicate();
                    synapse2.setSource(target);
                    synapse2.setTarget(source);
                    network.addSynapse(synapse2);
                }
            } else {
                return;
            }
        }


//        Iterator<Neuron> targets = getSortedNeuronList(targetNeurons, X_ORDER)
//                .iterator();
//
//        for (Iterator<Neuron> sources = getSortedNeuronList(sourceNeurons,
//                X_ORDER).iterator(); sources.hasNext();) {
//            Neuron source = (Neuron) sources.next();
//            if (targets.hasNext()) {
//                Neuron target = (Neuron) targets.next();
//                Synapse synapse = baseSynapse.duplicate();
//                synapse.setSource(source);
//                synapse.setTarget(target);
//                network.addSynapse(synapse);
//                // If bidirectional {
//                //      Synapse synapse2 = baseSynapse.duplicate();
//                 //     synapse2.setSource(target);
//                 //       synapse2.setTarget(source);
//                //  network.addSynapse(synapse2);     }
//            } else {
//                return;
//            }
//        }
    }

    /**
     * @return the baseSynapse
     */
    public Synapse getBaseSynapse() {
        return baseSynapse;
    }

    /**
     * @param baseSynapse the baseSynapse to set
     */
    public void setBaseSynapse(final Synapse baseSynapse) {
        this.baseSynapse = baseSynapse;
    }


    /**
     * @return the useBidirectionalConnections
     */
    public boolean isUseBidirectionalConnections() {
        return useBidirectionalConnections;
    }


    /**
     * @param useBidirectionalConnections the useBidirectionalConnections to set
     */
    public void setUseBidirectionalConnections(boolean useBidirectionalConnections) {
        this.useBidirectionalConnections = useBidirectionalConnections;
    }


    /**
     * @return the connectOrientation
     */
    public Comparator<Neuron> getConnectOrientation() {
        return connectOrientation;
    }


    /**
     * @param connectOrientation the connectOrientation to set
     */
    public void setConnectOrientation(Comparator<Neuron> connectOrientation) {
        this.connectOrientation = connectOrientation;
    }
}
