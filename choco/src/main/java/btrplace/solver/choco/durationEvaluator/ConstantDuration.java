/*
 * Copyright (c) 2012 University of Nice Sophia-Antipolis
 *
 * This file is part of btrplace.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package btrplace.solver.choco.durationEvaluator;

import btrplace.solver.choco.DurationEvaluator;

import java.util.UUID;

/**
 * Evaluate an action duration to a constant.
 *
 * @author Fabien Hermenier
 */
public class ConstantDuration implements DurationEvaluator {

    private int duration;

    /**
     * Make a new evaluator.
     *
     * @param d the estimated duration to accomplish the action. Must be strictly positive
     */
    public ConstantDuration(int d) {
        this.duration = d;
    }

    @Override
    public int evaluate(UUID e) {
        return duration;
    }

    @Override
    public String toString() {
        return new StringBuilder("d=").append(duration).toString();
    }
}
