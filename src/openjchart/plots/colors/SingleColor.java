/* OpenJChart : a free plotting library for the Java(tm) platform
 *
 * (C) Copyright 2009, by Erich Seifert and Michael Seifert.
 *
 * This file is part of OpenJChart.
 *
 * OpenJChart is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenJChart is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenJChart.  If not, see <http://www.gnu.org/licenses/>.
 */

package openjchart.plots.colors;

import java.awt.Color;

/**
 * Class that represents a ColorMapper with a single color.
 */
public class SingleColor implements ColorMapper {
	private Color color;

	/**
	 * Creates a new SingleColor object with the specified color.
	 * @param color
	 */
	public SingleColor(Color color) {
		this.color = color;
	}

	@Override
	public Color get(double value) {
		return color;
	}

	/**
	 * Returns the color of this ColorMapper.
	 * @return Color.
	 */
	public Color getColor() {
		return color;
	}

	/**
	 * Sets the color of this ColorMapper.
	 * @param color Color to be set.
	 */
	public void setColor(Color color) {
		this.color = color;
	}

}
