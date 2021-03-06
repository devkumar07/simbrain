package org.simbrain.network.matrix

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.simbrain.network.core.Network

class WeightMatrixConnectableTest {

    var net = Network()
    var na1 = NeuronArray(net, 2)
    var na2 = NeuronArray(net, 2)
    var wm = WeightMatrix(net, na1, na2)

    @Test
    internal fun `getWeightedInputs returns correct values`() {
        na1.activations = doubleArrayOf(1.0,-1.0)
        wm.diagonalize()
        assertArrayEquals(doubleArrayOf(1.0,-1.0), na2.weightedInputs, 0.0)
    }
}