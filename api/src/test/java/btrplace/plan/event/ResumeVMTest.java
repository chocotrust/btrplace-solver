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

package btrplace.plan.event;

import btrplace.model.DefaultMapping;
import btrplace.model.DefaultModel;
import btrplace.model.Mapping;
import btrplace.model.Model;
import btrplace.plan.VMStateTransition;
import btrplace.test.PremadeElements;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit tests for {@link ResumeVM}.
 *
 * @author Fabien Hermenier
 */
public class ResumeVMTest implements PremadeElements {

    @Test
    public void testInstantiate() {
        ResumeVM a = new ResumeVM(vm1, n1, n2, 3, 5);
        Assert.assertEquals(vm1, a.getVM());
        Assert.assertEquals(n1, a.getSourceNode());
        Assert.assertEquals(n2, a.getDestinationNode());
        Assert.assertEquals(3, a.getStart());
        Assert.assertEquals(5, a.getEnd());
        Assert.assertFalse(a.toString().contains("null"));
        Assert.assertEquals(a.getCurrentState(), VMStateTransition.VMState.sleeping);
        Assert.assertEquals(a.getNextState(), VMStateTransition.VMState.running);


    }

    @Test(dependsOnMethods = {"testInstantiate"})
    public void testApply() {
        Mapping map = new DefaultMapping();
        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addSleepingVM(vm1, n1);

        Model m = new DefaultModel(map);

        ResumeVM a = new ResumeVM(vm1, n1, n2, 3, 5);
        Assert.assertTrue(a.apply(m));
        Assert.assertEquals(map.getVMLocation(vm1), n2);
        Assert.assertTrue(map.getRunningVMs().contains(vm1));

        Assert.assertFalse(a.apply(m));
        Assert.assertEquals(map.getVMLocation(vm1), n2);

        map.addSleepingVM(vm1, n2);
        Assert.assertTrue(new ResumeVM(vm1, n2, n2, 3, 5).apply(m));

        Assert.assertFalse(new ResumeVM(vm1, n2, n1, 3, 5).apply(m));

        map.addReadyVM(vm1);
        Assert.assertFalse(new ResumeVM(vm1, n2, n1, 3, 5).apply(m));

        map.addOfflineNode(n1);
        Assert.assertFalse(new ResumeVM(vm1, n2, n1, 3, 5).apply(m));

        map.removeNode(n1);
        Assert.assertFalse(new ResumeVM(vm1, n2, n1, 3, 5).apply(m));
    }

    @Test(dependsOnMethods = {"testInstantiate"})
    public void testEquals() {
        ResumeVM a = new ResumeVM(vm1, n1, n2, 3, 5);
        ResumeVM b = new ResumeVM(vm1, n1, n2, 3, 5);
        Assert.assertFalse(a.equals(new Object()));
        Assert.assertTrue(a.equals(a));
        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());

        Assert.assertNotSame(a, new ResumeVM(vm1, n1, n2, 4, 5));
        Assert.assertNotSame(a, new ResumeVM(vm1, n1, n2, 3, 4));
        Assert.assertNotSame(a, new ResumeVM(vm2, n1, n2, 3, 5));
        Assert.assertNotSame(a, new ResumeVM(vm1, n3, n2, 3, 5));
        Assert.assertNotSame(a, new ResumeVM(vm1, n1, n3, 3, 5));

    }
}
