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

package btrplace.solver.choco;

import java.util.UUID;

/**
 * Interface to specify an action model that manipulate a VM.
 *
 * @author Fabien Hermenier
 */
public interface VMActionModel extends ActionModel {

    /**
     * Get the VM manipulated by the action.
     *
     * @return the VM identifier
     */
    UUID getVM();

    /**
     * Get the slice denoting the possible current placement of the subject on a node.
     *
     * @return a {@link Slice} that may be {@code null}
     */
    Slice getCSlice();

    /**
     * Get the slice denoting the possible future placement off the subject
     *
     * @return a {@link Slice} that may be {@code null}
     */
    Slice getDSlice();
}
