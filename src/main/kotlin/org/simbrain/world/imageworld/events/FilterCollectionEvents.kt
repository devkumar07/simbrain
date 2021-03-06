package org.simbrain.world.imageworld.events

import org.simbrain.util.Event
import org.simbrain.world.imageworld.filters.FilterCollection
import org.simbrain.world.imageworld.filters.Filter
import java.beans.PropertyChangeSupport
import java.util.function.Consumer

/**
 * See [Event].
 */
class FilterCollectionEvents(val source : FilterCollection) : Event(PropertyChangeSupport(source)) {

    fun onFilterAdded(handler: Consumer<Filter>) = "FilterAdded".itemAddedEvent(handler)
    fun fireFilterAdded(filter: Filter) = "FilterAdded"(new = filter)

    fun onFilterRemoved(handler: Consumer<Filter>) =
        "FilterRemoved".itemRemovedEvent(handler)
    fun fireFilterRemoved(filter: Filter) = "FilterRemoved"(old = filter)

    /**
     * The current filter has changed.
     */
    fun onFilterChanged(handler: Runnable) = "FilterChanged".event(handler)
    fun fireFilterChanged() = "FilterChanged"()

}