package org.simbrain.network.gui

import org.piccolo2d.PCamera
import org.piccolo2d.PCanvas
import org.piccolo2d.event.PMouseWheelZoomEventHandler
import org.piccolo2d.util.PBounds
import org.piccolo2d.util.PPaintContext
import org.simbrain.network.NetworkComponent
import org.simbrain.network.NetworkModel
import org.simbrain.network.connections.QuickConnectionManager
import org.simbrain.network.core.*
import org.simbrain.network.groups.NeuronCollection
import org.simbrain.network.groups.NeuronGroup
import org.simbrain.network.groups.Subnetwork
import org.simbrain.network.groups.SynapseGroup
import org.simbrain.network.gui.UndoManager.UndoableAction
import org.simbrain.network.gui.actions.edit.ToggleAutoZoom
import org.simbrain.network.gui.nodes.*
import org.simbrain.network.gui.nodes.neuronGroupNodes.CompetitiveGroupNode
import org.simbrain.network.gui.nodes.neuronGroupNodes.SOMGroupNode
import org.simbrain.network.gui.nodes.subnetworkNodes.*
import org.simbrain.network.matrix.NeuronArray
import org.simbrain.network.matrix.WeightMatrix
import org.simbrain.network.matrix.WeightMatrixConnectable
import org.simbrain.network.smile.SmileClassifier
import org.simbrain.network.subnetworks.*
import org.simbrain.network.trainers.LMSIterative
import org.simbrain.network.trainers.TrainingSet
import org.simbrain.util.complement
import org.simbrain.util.genericframe.GenericJDialog
import org.simbrain.util.widgets.EditablePanel
import org.simbrain.util.widgets.ToggleButton
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.Timer
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlin.concurrent.timerTask

/**
 * Main GUI representation of a [Network].
 */
class NetworkPanel(val networkComponent: NetworkComponent) : JPanel() {

    /**
     * Main Piccolo canvas object.
     *
     * @see https://github.com/piccolo2d/piccolo2d.java
     */
    val canvas = PCanvas()

    /**
     * Reference to the model network
     */
    val network = networkComponent.network

    /**
     * Manage selection events where the "green handle" is added to nodes and other [NetworkModel]s
     * when the lasso is pulled over them.  Also keeps track of source nodes (but those events are
     * handled by keybindings).
     */
    val selectionManager = NetworkSelectionManager(this).apply {
        setUpSelectionEvents()
    }

    val networkActions = NetworkActions(this)

    /**
     * Associates neurons with neuron nodes for use mainly in creating synapse nodes.
     */
    val neuronNodeMapping: Map<Neuron, NeuronNode> = HashMap()

    val timeLabel = TimeLabel(this).apply { update() }

    var autoZoom = true
        set(value) {
            field = value
            zoomToFitPage()
        }

    var editMode: EditMode = EditMode.SELECTION
        set(newEditMode) {
            val oldEditMode = editMode
            field = newEditMode
            if (editMode == EditMode.WAND) {
                editMode.resetWandCursor()
            }
            firePropertyChange("editMode", oldEditMode, editMode)
            cursor = editMode.cursor
        }

    var showTime = true

    private val toolbars: JPanel = JPanel(BorderLayout())

    val mainToolBar = createMainToolBar()

    val runToolBar = createRunToolBar().apply{isVisible = false}

    val editToolBar = createEditToolBar()

    var backgroundColor = Color.white!!

    val isRunning
        get() = network.isRunning

    /**
     * How much to nudge objects per key click.
     */
    var nudgeAmount = 2.0

    /**
     * Text object event handler.
     */
    val textHandle: TextEventHandler = TextEventHandler(this)

    /**
     * Manages keyboard-based connections.
     */
    val quickConnector = QuickConnectionManager()

    /**
     * Manages placement of new nodes, groups, etc.
     */
    val placementManager = PlacementManager()

    /**
     * Undo Manager
     */
    val undoManager = UndoManager()

    /**
     * Set to 3 since update neurons, synapses, and groups each decrement it by 1. If 0, update is complete.
     */
    private val updateComplete = AtomicInteger(0)

    /**
     * Whether loose synapses are visible or not.
     */
    var looseWeightsVisible = true
        set(value) {
            field = value
            filterScreenElements<SynapseNode>().forEach { it.visible = value }
        }

    /**
     * Whether to display update priorities.
     */
    var prioritiesVisible = false
        set(value) {
            field = value
            filterScreenElements<NeuronNode>().forEach { it.setPriorityView(value) }
        }

    /**
     * Turn GUI on or off.
     */
    var guiOn = true
        set(guiOn) {
            if (guiOn) {
                this.setUpdateComplete(false)
                //this.updateSynapseNodes()
                updateComplete.decrementAndGet()
            }
            field = guiOn
        }

    /** TODO: Javadoc. */
    fun setUpdateComplete(updateComplete: Boolean) {
        if (!updateComplete && this.updateComplete.get() != 0) {
            return
        }
        this.updateComplete.set(if (updateComplete) 0 else 3)
    }

    val zoomToFitPage = fun (): (Boolean) -> Unit {
        var timer: Timer? = null
        return fun (forceZoom: Boolean) {

            // Implements debounce. If many requests are made they will all be cancelled, until the last one.
            timer?.cancel()

            timer = Timer().apply {
                schedule(timerTask {
                    if (autoZoom && editMode.isSelection || forceZoom) {
                        val filtered = canvas.layer.getUnionOfChildrenBounds(null)
                        val adjustedFiltered = PBounds(filtered.getX() - 10, filtered.getY() - 10,
                            filtered.getWidth() + 20, filtered.getHeight() + 20)
                        canvas.camera.setViewBounds(adjustedFiltered)
                    }
                }, 5)
            }
        }
    }()

    /**
     * Rescales the camera so that all objects in the canvas can be seen. Compare "zoom to fit page" in draw programs.
     *
     * @param forceZoom if true force the zoom to happen
     */
    fun zoomToFitPage() {
        zoomToFitPage(false)
    }

    /**
     * Returns all nodes in the canvas.
     */
    val screenElements get() = canvas.layer.allNodes.filterIsInstance<ScreenElement>()

    /**
     * Filter [ScreenElement]s using a generic type.
     */
    inline fun <reified T : ScreenElement> filterScreenElements() = canvas.layer.allNodes.filterIsInstance<T>()
    fun <T : ScreenElement> filterScreenElements(clazz: Class<T>) =
            canvas.layer.allNodes.filterIsInstance(clazz)

    /**
     * Add a screen element to the network panel and rezoom the page.
     */
    private inline fun <T : ScreenElement> addScreenElement(block: () -> T) = block().also {
        canvas.layer.addChild(it)
        zoomToFitPage()
    }

    /**
     * Add a neuron and use placement manager to lay it out.
     */
    fun placeNeuron(neuron: Neuron) {
        placementManager.addNewModelObject(neuron)

        undoManager.addUndoableAction(object : UndoableAction {
            override fun undo() {
                network.delete(neuron)
            }
            override fun redo() {
                network.addNetworkModel(neuron)
            }
        })
        network.addNetworkModel(neuron)
        zoomToFitPage()
    }

    private fun createNode(model: NetworkModel) {
        when(model) {
            is Neuron -> createNode(model)
            is Synapse -> createNode(model)
            is NeuronArray -> createNode(model)
            is NeuronCollection -> createNode(model)
            is NeuronGroup -> createNode(model)
            is SynapseGroup -> createNode(model)
            is WeightMatrix -> createNode(model)
            is Subnetwork -> createNode(model)
            is NetworkTextObject -> createNode(model)
            is SmileClassifier -> createNode(model)
        }
    }

    fun createNode(neuron: Neuron) = addScreenElement {
        Neuron.tempDebugNan(neuron)
        NeuronNode(this, neuron).also {
            (neuronNodeMapping as HashMap)[neuron] = it
            selectionManager.set(it)
        }
    }

    fun createNode(synapse: Synapse) = addScreenElement {
        val source = neuronNodeMapping[synapse.source] ?: throw IllegalStateException("Neuron node does not exist")
        val target = neuronNodeMapping[synapse.target] ?: throw IllegalStateException("Neuron node does not exist")
        SynapseNode(this, source, target, synapse)
    }.also { it.lowerToBottom() }

    fun createNode(neuronGroup: NeuronGroup) = addScreenElement {

        fun createNeuronGroupNode() = when (neuronGroup) {
            is SOMGroup -> SOMGroupNode(this, neuronGroup)
            is CompetitiveGroup -> CompetitiveGroupNode(this, neuronGroup)
            else -> NeuronGroupNode(this, neuronGroup)
        }

        neuronGroup.applyLayout()
        val neuronNodes = neuronGroup.neuronList.map { neuron -> createNode(neuron) }
        createNeuronGroupNode().apply { addNeuronNodes(neuronNodes) }
    }

    fun createNode(neuronArray: NeuronArray) = addScreenElement { NeuronArrayNode(this, neuronArray) }

//    fun createNode(multiLayerNet: MultiLayerNet) = addScreenElement {
//        MultiLayerNetworkNode(this, multiLayerNet)
//    }

    fun createNode(classifier : SmileClassifier) = addScreenElement {
        SmileClassifierNode(this, classifier)
    }

    fun createNode(neuronCollection: NeuronCollection) = addScreenElement {
        val neuronNodes = neuronCollection.neuronList.map {
            neuronNodeMapping[it] ?: throw IllegalStateException("Neuron node does not exist")
        }
        NeuronCollectionNode(this, neuronCollection).apply { addNeuronNodes(neuronNodes) }
    }

    fun createNode(synapseGroup: SynapseGroup) = addScreenElement {
        SynapseGroupNode(this, synapseGroup)
    }.also { it.lowerToBottom() }

    fun createNode(weightMatrix: WeightMatrix) = addScreenElement {
        WeightMatrixNode(this, weightMatrix).also { it.lower() }
    }

    fun createNode(text: NetworkTextObject) = addScreenElement {
        TextNode(this, text).apply {
            if(text.inputEvent != null) {
                textHandle.startEditing(text.inputEvent, this.pStyledText);
            }
        }
    }

    fun createNode(subnetwork: Subnetwork) = addScreenElement {
        fun createSubNetwork() = when (subnetwork) {
            is Hopfield -> HopfieldNode(this, subnetwork)
            is CompetitiveNetwork -> CompetitiveNetworkNode(this, subnetwork)
            is SOMNetwork -> SOMNetworkNode(this, subnetwork)
            // is EchoStateNetwork -> ESNNetworkNode(this, subnetwork)
            //is SimpleRecurrentNetwork -> SRNNetworkNode(this, subnetwork)
            is BackpropNetwork -> BackpropNetworkNode(this, subnetwork)
            // is LMSNetwork -> LMSNetworkNode(this, subnetwork)
            else -> SubnetworkNode(this, subnetwork)
        }

        val neuronGroupNodes = subnetwork.neuronGroupList.map { createNode(it) }
        val neuronArrayNodes = subnetwork.naList.map { createNode(it) }
        val weightMatrixNodes = subnetwork.weightMatrixList.map { createNode(it) }
        val synapseGroupNodes = subnetwork.synapseGroupList.map { createNode(it) }

        createSubNetwork().apply {
            neuronGroupNodes.forEach { addNode(it) }
            neuronArrayNodes.forEach { addNode(it) }
            weightMatrixNodes.forEach { addNode(it) }
            synapseGroupNodes.forEach { addNode(it) }
        }

    }

    fun deleteSelectedObjects() {

        fun deleteGroup(interactionBox: InteractionBox) {
            interactionBox.parent.let { groupNode ->
                if (groupNode is ScreenElement) {
                    network.delete(groupNode.model)
                }
            }
        }

        fun delete(screenElement: ScreenElement) {
            with(network) {
                when (screenElement) {
                    is NeuronNode -> {
                        network.delete(screenElement.model)

                        undoManager.addUndoableAction(object : UndoableAction {
                            override fun undo() {
                                network.addNetworkModel(screenElement.model)
                            }
                            override fun redo() {
                                network.delete(screenElement.model)
                            }
                        })
                    }
                    is InteractionBox -> deleteGroup(screenElement)
                    else -> delete(screenElement.model)
                }
            }
        }

        selectionManager.selection.forEach { delete(it) }

        // Zoom events are costly so only zoom after main deletion events
        zoomToFitPage(true)
    }

    private fun createEditToolBar() = CustomToolBar().apply {
        with(networkActions) {
            networkEditingActions.forEach { add(it) }
            add(clearNodeActivationsAction)
            // TODO
            add(randomizeObjectsAction)
        }
    }

    fun copy() {
        if (selectionManager.isEmpty) return

        Clipboard.clear()
        Clipboard.add(selectionManager.selectedModels)
        placementManager.setNewCopy()
    }

    fun cut() {
        copy()
        deleteSelectedObjects()
    }

    fun paste() {
        Clipboard.paste(this)
    }

    fun duplicate() {
        if (selectionManager.isEmpty) return

        copy()
        paste()
    }

    fun alignHorizontal() {
        val neurons = selectionManager.filterSelectedModels<Neuron>()
        val minY = neurons.map { it.y }.minOrNull() ?: Double.MAX_VALUE
        neurons.forEach { it.y = minY }
        repaint()
    }

    fun alignVertical() {
        val neurons = selectionManager.filterSelectedModels<Neuron>()
        val minX = neurons.map { it.x }.minOrNull() ?: Double.MAX_VALUE
        neurons.forEach { it.x = minX }
        repaint()
    }

    fun spaceHorizontal() {
        val neurons = selectionManager.filterSelectedModels<Neuron>()
        if (neurons.size > 1) {
            val sortedNeurons = neurons.sortedBy { it.x }
            val min = neurons.first().x
            val max = neurons.last().x
            val spacing = (max - min) / neurons.size - 1

            sortedNeurons.forEachIndexed { i, neuron -> neuron.x = min + spacing * i }
        }
        repaint()
    }

    fun spaceVertical() {
        val neurons = selectionManager.filterSelectedModels<Neuron>()
        if (neurons.size > 1) {
            val sortedNeurons = neurons.sortedBy { it.y }
            val min = neurons.first().y
            val max = neurons.last().y
            val spacing = (max - min) / neurons.size - 1

            sortedNeurons.forEachIndexed { i, neuron -> neuron.y = min + spacing * i }
        }
        repaint()
    }

    fun nudge(dx: Int, dy: Int) {
        selectionManager.filterSelectedModels<Neuron>()
                .forEach { it.offset(dx * nudgeAmount, dy * nudgeAmount) }
    }

    fun toggleClamping() {
        selectionManager.filterSelectedModels<Neuron>().forEach { it.isClamped = !it.isClamped }
        selectionManager.filterSelectedNodes<SynapseNode>().forEach {
            with(it.synapse) { isFrozen = !isFrozen }

            // TODO: this should happen via an event
            //   but firing events from setFrozen causes problems
            //   when opening saved networks
            it.updateClampStatus()
        }
        revalidate()
    }

    fun incrementSelectedObjects() {
        with(selectionManager) {
            filterSelectedModels<Neuron>().forEach { it.updateRule.incrementActivation(it) }
            filterSelectedNodes<SynapseNode>().forEach {
                it.synapse.incrementWeight()
                it.updateColor()
                it.updateDiameter()
            }
            filterSelectedModels<NeuronArray>().forEach { it.increment() }
            filterSelectedModels<WeightMatrix>().forEach { it.increment() }
        }
    }

    fun decrementSelectedObjects() {
        with(selectionManager) {
            filterSelectedModels<Neuron>().forEach { it.updateRule.decrementActivation(it) }
            filterSelectedNodes<SynapseNode>().forEach {
                it.synapse.decrementWeight()
                it.updateColor()
                it.updateDiameter()
            }
            filterSelectedModels<NeuronArray>().forEach { it.decrement() }
            filterSelectedModels<WeightMatrix>().forEach { it.decrement() }
        }
    }

    fun contextualIncrementSelectedObjects() {
        selectionManager.filterSelectedModels<Synapse>().forEach { it.incrementWeight() }
        selectionManager.filterSelectedModels<Neuron>().forEach { it.updateRule.contextualIncrement(it) }
        selectionManager.filterSelectedModels<NeuronArray>().forEach { it.increment() }
        selectionManager.filterSelectedModels<WeightMatrix>().forEach { it.increment() }
    }

    fun contextualDecrementSelectedObjects() {
        selectionManager.filterSelectedModels<Synapse>().forEach { it.decrementWeight() }
        selectionManager.filterSelectedModels<Neuron>().forEach { it.updateRule.contextualDecrement(it) }
        selectionManager.filterSelectedModels<NeuronArray>().forEach { it.decrement() }
        selectionManager.filterSelectedModels<WeightMatrix>().forEach { it.decrement() }
    }

    fun clearSelectedObjects() {
        with(selectionManager) {
            filterSelectedModels<Neuron>().forEach { it.clear() }
            filterSelectedModels<Synapse>().forEach { it.forceSetStrength(0.0) }
            filterSelectedModels<NeuronArray>().forEach { it.clear() }
            filterSelectedModels<NeuronGroup>().forEach { it.clear() }
            filterSelectedModels<WeightMatrix>().forEach { it.clear() }
        }
    }

    fun selectNeuronsInNeuronGroups() {
        selectionManager.filterSelectedNodes<NeuronGroupNode>().forEach { it.selectNeurons() }
    }

    fun clearNeurons() {
        filterScreenElements<NeuronNode>().forEach { it.neuron.clear() }
    }

    /**
     * Connect source and target model items.
     * <br></br>
     * If a either source or target model items are ND4J, connect with a weight matrix
     * <br></br>
     * If a pair of source and target items are neuron groups, connect with a synapse group
     * <br></br>
     * If either member of a pair is a neuron collection or a set of loose neurons, then connect using neurons on both
     * sides, using quick connect (e.g. if connecting neuron collection to neuron group, connect to the neurons "inside"
     * of neuron group).
     */
    fun connectSelectedModels() {


        with(selectionManager) {

            // Connect first selected neuron groups with a synapse group, if any are selected
            val src = networkPanel.selectionManager.filterSelectedSourceModels(NeuronGroup::class.java)
            val tar = networkPanel.selectionManager.filterSelectedModels(NeuronGroup::class.java)
            if (src.isNotEmpty() && tar.isNotEmpty()) {
                displaySynapseGroupDialog(this.networkPanel, src.get(0), tar.get(0))
                return;
            }

            // Connect loose neurons with loose synapses using quick connector
            val sourceNeurons = filterSelectedSourceModels<Neuron>() +
                    filterSelectedSourceModels<NeuronCollection>().flatMap { it.neuronList } +
                    filterSelectedSourceModels<NeuronGroup>().flatMap { it.neuronList }
            val targetNeurons = filterSelectedModels<Neuron>() +
                    filterSelectedModels<NeuronCollection>().flatMap { it.neuronList } +
                    filterSelectedModels<NeuronGroup>().flatMap { it.neuronList }
            quickConnector.applyCurrentConnection(network, sourceNeurons, targetNeurons)
        }
    }

    /**
     * Connect all selected [WeightMatrixConnectable]s with [WeightMatrix] objects.
     */
    fun connectWithWeightMatrix() {
        with(selectionManager) {
            val sources = filterSelectedSourceModels<WeightMatrixConnectable>()
            val targets = filterSelectedModels<WeightMatrixConnectable>()

            for (source in sources) {
                for (target in targets) {
                    network.addNetworkModel(WeightMatrix(network, source, target));
                }
            }
        }
    }

    // TODO: Move to NetworkDialogs.kt
    @Deprecated("Consider removing or refactor out of NetworkPanel")
    fun displayPanel(panel: JPanel, title: String) = GenericJDialog().apply {
        if (this is JInternalFrame) {
            addInternalFrameListener(object : InternalFrameAdapter() {
                override fun internalFrameClosed(e: InternalFrameEvent) {
                    if (panel is EditablePanel) {
                        panel.commitChanges()
                    }
                }
            })
        }
        this.title = title
        contentPane = panel
        pack()
        isVisible = true
    }

    // TODO: Move to NetworkDialogs.kt
    @Deprecated("Consider removing or refactor out of NetworkPanel")
    fun displayPanelInWindow(panel: JPanel, title: String) = GenericJDialog().apply {
        this.title = title
        contentPane = panel
        pack()
        isVisible = true
    }

    private fun createMainToolBar() = CustomToolBar().apply {
        with(networkActions) {
            networkModeActions.forEach { add(it) }
            addSeparator()
            add(ToggleAutoZoom(this@NetworkPanel))
        }
    }

    private fun createRunToolBar() = CustomToolBar().apply {
        with(networkActions) {
            add(iterateNetworkAction)
            add(ToggleButton(networkControlActions))
        }
    }

    private fun addNetworkListeners() {
        val event = network.events
        event.onModelAdded { createNode(it) }
        event.onModelRemoved {
            it.events.fireDeleted()
            // TODO: For batch delete ideally this would only be called once
            zoomToFitPage()
        }
        event.onUpdateTimeDisplay { timeLabel.update() }
        event.onUpdateCompleted { repaint() }

    }

    private fun NetworkSelectionManager.setUpSelectionEvents() {
        events.apply {
            onSelection { old, new ->
                val (removed, added) = old complement new
                removed.forEach { NodeHandle.removeSelectionHandleFrom(it) }
                added.forEach {
                    if (it is InteractionBox) {
                        NodeHandle.addSelectionHandleTo(it, NodeHandle.INTERACTION_BOX_SELECTION_STYLE)
                    } else {
                        NodeHandle.addSelectionHandleTo(it)
                    }
                }
            }
            onSourceSelection { old, new ->
                val (removed, added) = old complement new
                removed.forEach { NodeHandle.removeSourceHandleFrom(it) }
                added.forEach {
                    if (it is InteractionBox) {
                        NodeHandle.addSourceHandleTo(it, NodeHandle.INTERACTION_BOX_SOURCE_STYLE)
                    } else {
                        NodeHandle.addSourceHandleTo(it)
                    }
                }
            }
        }
    }

    fun showLMS() {
        val sources = selectionManager.filterSelectedSourceModels<Neuron>()
        val targets = selectionManager.filterSelectedModels<Neuron>()
        val sourceActivations = arrayOf(sources.activations.toDoubleArray())
        val targetActivations = arrayOf(targets.activations.toDoubleArray())
        val ts = TrainingSet(sourceActivations, targetActivations)
        val lms = LMSIterative(sources, targets, ts)
        showLMSDialog(lms)
    }

    /**
     * TODO: Work in progress.
     */
    fun undo() {
        println("Initial testing on undo...")
        undoManager.undo()
    }

    /**
     * TODO: Work in progress.
     */
    fun redo() {
        println("Initial testing on redo...")
        undoManager.redo()
    }

    /**
     * Main initialization of the network panel.
     */
    init {
        super.setLayout(BorderLayout())

        canvas.apply {
            // Always render in high quality
            setDefaultRenderQuality(PPaintContext.HIGH_QUALITY_RENDERING)
            animatingRenderQuality = PPaintContext.HIGH_QUALITY_RENDERING
            interactingRenderQuality = PPaintContext.HIGH_QUALITY_RENDERING

            // Remove default event listeners
            removeInputEventListener(panEventHandler)
            removeInputEventListener(zoomEventHandler)

            // Event listeners
            addInputEventListener(MouseEventHandler(this@NetworkPanel))
            addInputEventListener(ContextMenuEventHandler(this@NetworkPanel))
            addInputEventListener(PMouseWheelZoomEventHandler().apply { zoomAboutMouse() })
            addInputEventListener(textHandle)
            addInputEventListener(WandEventHandler(this@NetworkPanel));

            // Don't show text when the canvas is sufficiently zoomed in
            camera.addPropertyChangeListener(PCamera.PROPERTY_VIEW_TRANSFORM) {
                filterScreenElements<NeuronNode>().forEach { it.updateTextVisibility() }
            }
        }

        // Init network change listeners
        addNetworkListeners()

        toolbars.apply {

            cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
            val flowLayout = FlowLayout(FlowLayout.LEFT).apply { hgap = 0; vgap = 0 }
            add("Center", JPanel(flowLayout).apply {
                add(mainToolBar)
                add(runToolBar)
                add(editToolBar)
            })
        }

        add("North", toolbars)
        add("Center", canvas)
        add("South", JToolBar().apply { add(timeLabel) })

        // Register support for tool tips
        // TODO: might be a memory leak, if not unregistered when the parent frame is removed
        // TODO: copy from old code. Re-verify.
        ToolTipManager.sharedInstance().registerComponent(this)

        addKeyBindings()

        // Repaint whenever window is opened or changed.
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(arg0: ComponentEvent) {
                zoomToFitPage()
            }
        })

        // Add all network elements (important for de-serializing)
        network.allModelsInDeserializationOrder.forEach{ createNode(it) }

    }

}