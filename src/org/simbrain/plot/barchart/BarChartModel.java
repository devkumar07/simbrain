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
package org.simbrain.plot.barchart;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jfree.data.category.DefaultCategoryDataset;
import org.simbrain.plot.ChartDataSource;
import org.simbrain.plot.ChartModel;
import org.simbrain.workspace.*;

import com.thoughtworks.xstream.XStream;

/**
 * Data for a JFreeChart pie chart.
 */
public class BarChartModel extends ChartModel {

    /**
     * Bar encapsulates a single data column in the BarChartModel.
     */
    public class Bar implements ChartDataSource {
        private String description;

        Bar(String description) {
            this.description = description;
            dataset.addValue((Number)0, 1, description);
        }

        public String getDescription() {
            return description;
        }

        @Consumable(idMethod="getDescription")
        public void setValue(double value) {
            dataset.setValue((Number)value, 1, description);
        }
    }

    /** JFreeChart dataset for bar charts. */
    private DefaultCategoryDataset dataset = new DefaultCategoryDataset();

    private List<Bar> bars = new ArrayList<Bar>();

    /** Color of bars in barchart. */
    private Color barColor = Color.red;

    /** Auto range bar chart. */
    private boolean autoRange = true;

    /** Maximum range. */
    private double upperBound = 10;

    /** Minimum range. */
    private double lowerBound = 0;

    /** Bar chart model constructor. */
    public BarChartModel() {}

    /**
     * Return JFreeChart category dataset.
     * @return dataset
     */
    public DefaultCategoryDataset getDataset() {
        return dataset;
    }

    /**
     * Returns a properly initialized xstream object.
     * @return the XStream object
     */
    public static XStream getXStream() {
        XStream xstream = ChartModel.getXStream();
        return xstream;
    }

    /**
     * Standard method call made to objects after they are deserialized. See:
     * http://java.sun.com/developer/JDCTechTips/2002/tt0205.html#tip2
     * http://xstream.codehaus.org/faq.html
     *
     * @return Initialized object.
     */
    private Object readResolve() {
        return this;
    }

    /**
     * @return the barColor
     */
    public Color getBarColor() {
        return barColor;
    }

    /**
     * @param barColor the barColor to set
     */
    public void setBarColor(final Color barColor) {
        this.barColor = barColor;
        fireSettingsChanged();
    }

    /**
     * @return the autoRange
     */
    public boolean isAutoRange() {
        return autoRange;
    }

    /**
     * @param autoRange the autoRange to set
     */
    public void setAutoRange(final boolean autoRange) {
        this.autoRange = autoRange;
        fireSettingsChanged();
    }

    /**
     * @return the upperBound
     */
    public double getUpperBound() {
        return upperBound;
    }

    /**
     * @param upperBound the upperBound to set
     */
    public void setUpperBound(final double upperBound) {
        this.upperBound = upperBound;
        fireSettingsChanged();
    }

    /**
     * @return the lowerBound
     */
    public double getLowerBound() {
        return lowerBound;
    }

    /**
     * @param lowerBound the lowerBound to set
     */
    public void setLowerBound(final double lowerBound) {
        this.lowerBound = lowerBound;
        fireSettingsChanged();
    }

    /**
     * @param lowerBound the lower range boundary.
     * @param upperBound the upper range boundary.
     */
    public void setRange(double lowerBound, double upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        fireSettingsChanged();
    }

    /** Return a list of all bars used by this BarChartModel. */
    public List<Bar> getBars() {
        return bars;
    }



    /** Dummy method for coupling to the bar chart. Couplings to this will be redirected to a new bar. */
    @Consumable(idMethod="getId")
    public void addBar(double value) {}

    /** Identify this model. */
    public String getId() {
        return "BarChart";
    }

    /**
     * Create specified number of bars.
     * @param numBars number of bars to add.
     */
    public void addBars(int numBars) {
        for (int i = 0; i < numBars; i++) {
            addBar();
        }
    }

    /** Add a new bar to the dataset. */
    public Bar addBar() {
        return addBar("Bar" + (bars.size() + 1));
    }

    public Bar addBar(String description) {
        Bar bar = new Bar(description);
        bars.add(bar);
        fireDataSourceAdded(bar);
        return bar;
    }

    /**
     * Removes the last bar from the bar chart data.
     */
    public void removeBar() {
        if (dataset.getColumnCount() > 0) {
            Bar bar = bars.remove(bars.size() - 1);
            dataset.removeColumn(bar.getDescription());
            fireDataSourceRemoved(bar);
        }
    }

    public void removeBar(String description) {
        Optional<Bar> bar = getBar(description);
        if (bar.isPresent()) {
            bars.remove(bar.get());
            dataset.removeColumn(description);
            fireDataSourceRemoved(bar.get());
        }
    }

    public Optional<Bar> getBar(String description) {
        for (Bar bar : bars) {
            if (bar.getDescription().equals(description)) {
                return Optional.of(bar);
            }
        }
        return Optional.empty();
    }

}
