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

package btrplace.solver;

import btrplace.model.DefaultMapping;
import btrplace.model.DefaultModel;
import btrplace.model.Model;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Basic test for {@link SolverException}.
 *
 * @author Fabien Hermenier
 */
public class SolverExceptionTest {

    @Test
    public void testBasic() {
         Model mo = new DefaultModel(new DefaultMapping());
        SolverException ex = new SolverException(mo, "foo");
        Assert.assertEquals(ex.getModel(), mo);
        Assert.assertEquals(ex.getMessage(), "foo");
    }
}
