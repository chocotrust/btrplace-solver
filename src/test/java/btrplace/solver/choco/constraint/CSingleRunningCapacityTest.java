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

package btrplace.solver.choco.constraint;

import btrplace.model.*;
import btrplace.model.constraint.Ready;
import btrplace.model.constraint.Running;
import btrplace.model.constraint.SingleRunningCapacity;
import btrplace.plan.Action;
import btrplace.plan.DefaultReconfigurationPlan;
import btrplace.plan.ReconfigurationPlan;
import btrplace.plan.event.BootVM;
import btrplace.plan.event.ResumeVM;
import btrplace.plan.event.ShutdownVM;
import btrplace.plan.event.SuspendVM;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ChocoReconfigurationAlgorithm;
import btrplace.solver.choco.DefaultChocoReconfigurationAlgorithm;
import btrplace.solver.choco.durationEvaluator.ConstantDuration;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.*;

/**
 * Unit tests for {@link CSingleRunningCapacity}.
 *
 * @author Fabien Hermenier
 */
public class CSingleRunningCapacityTest {


    @Test
    public void testInstantiation() {
        SingleRunningCapacity b = new SingleRunningCapacity(Collections.singleton(UUID.randomUUID()), 1);
        CSingleRunningCapacity c = new CSingleRunningCapacity(b);
        Assert.assertEquals(b, c.getAssociatedConstraint());
    }

    @Test
    public void testDiscreteResolution() throws SolverException {
        UUID vm1 = UUID.randomUUID();
        UUID vm2 = UUID.randomUUID();
        UUID vm3 = UUID.randomUUID();
        UUID n1 = UUID.randomUUID();
        Mapping map = new DefaultMapping();
        map.addOnlineNode(n1);
        map.addRunningVM(vm1, n1);
        map.addRunningVM(vm2, n1);
        map.addReadyVM(vm3);
        Model mo = new DefaultModel(map);
        List<SatConstraint> l = new ArrayList<SatConstraint>();
        l.add(new Running(Collections.singleton(vm1)));
        l.add(new Ready(Collections.singleton(vm2)));
        l.add(new Running(Collections.singleton(vm3)));
        SingleRunningCapacity x = new SingleRunningCapacity(map.getAllNodes(), 2);
        x.setContinuous(false);
        l.add(x);
        ChocoReconfigurationAlgorithm cra = new DefaultChocoReconfigurationAlgorithm();
        cra.labelVariables(true);
        cra.getDurationEvaluators().register(ShutdownVM.class, new ConstantDuration(10));
        ReconfigurationPlan plan = cra.solve(mo, l);
        Iterator<Action> ite = plan.getActions().iterator();
        Assert.assertEquals(2, plan.getSize());
        Assert.assertEquals(SatConstraint.Sat.SATISFIED, x.isSatisfied(plan.getResult()));
    }

    @Test
    public void testContinuousResolution() throws SolverException {
        UUID vm1 = UUID.randomUUID();
        UUID vm2 = UUID.randomUUID();
        UUID vm3 = UUID.randomUUID();
        UUID n1 = UUID.randomUUID();
        Mapping map = new DefaultMapping();
        map.addOnlineNode(n1);
        map.addRunningVM(vm1, n1);
        map.addRunningVM(vm2, n1);
        map.addReadyVM(vm3);
        Model mo = new DefaultModel(map);
        List<SatConstraint> l = new ArrayList<SatConstraint>();
        l.add(new Running(Collections.singleton(vm1)));
        l.add(new Ready(Collections.singleton(vm2)));
        l.add(new Running(Collections.singleton(vm3)));
        SingleRunningCapacity sc = new SingleRunningCapacity(map.getAllNodes(), 2);
        sc.setContinuous(true);
        l.add(sc);
        ChocoReconfigurationAlgorithm cra = new DefaultChocoReconfigurationAlgorithm();
        cra.setTimeLimit(3);
        cra.labelVariables(true);
        cra.getDurationEvaluators().register(ShutdownVM.class, new ConstantDuration(10));
        ReconfigurationPlan plan = cra.solve(mo, l);
        Assert.assertNotNull(plan);
        System.out.println(plan);
        Iterator<Action> ite = plan.getActions().iterator();
        Assert.assertEquals(2, plan.getSize());
        Action a1 = ite.next();
        Action a2 = ite.next();
        Assert.assertTrue(a1 instanceof ShutdownVM);
        Assert.assertTrue(a2 instanceof BootVM);
        Assert.assertTrue(a1.getEnd() <= a2.getStart());
    }

    @Test
    public void testGetMisplaced() {
        Mapping m = new DefaultMapping();
        UUID n1 = UUID.randomUUID();
        UUID n2 = UUID.randomUUID();
        m.addOnlineNode(n1);
        m.addOnlineNode(n2);
        UUID vm1 = UUID.randomUUID();
        UUID vm2 = UUID.randomUUID();
        UUID vm3 = UUID.randomUUID();
        UUID vm4 = UUID.randomUUID();
        m.addRunningVM(vm1, n1);
        m.addReadyVM(vm2);

        m.addRunningVM(vm3, n2);
        m.addReadyVM(vm4);
        Model mo = new DefaultModel(m);

        SingleRunningCapacity c = new SingleRunningCapacity(m.getAllNodes(), 1);
        CSingleRunningCapacity cc = new CSingleRunningCapacity(c);

        Assert.assertTrue(cc.getMisPlacedVMs(mo).isEmpty());
        m.addRunningVM(vm4, n2);
        Assert.assertEquals(m.getRunningVMs(n2), cc.getMisPlacedVMs(mo));
        m.addRunningVM(vm2, n1);
        Assert.assertEquals(m.getAllVMs(), cc.getMisPlacedVMs(mo));
    }

    @Test
    public void testIsSatisfied() {
        Mapping m = new DefaultMapping();
        UUID n1 = UUID.randomUUID();
        UUID n2 = UUID.randomUUID();
        m.addOnlineNode(n1);
        m.addOnlineNode(n2);
        UUID vm1 = UUID.randomUUID();
        UUID vm2 = UUID.randomUUID();
        UUID vm3 = UUID.randomUUID();
        UUID vm4 = UUID.randomUUID();
        m.addRunningVM(vm1, n1);
        m.addReadyVM(vm2);

        m.addRunningVM(vm3, n2);
        m.addReadyVM(vm4);
        Model mo = new DefaultModel(m);

        SingleRunningCapacity c = new SingleRunningCapacity(m.getAllNodes(), 1);
        CSingleRunningCapacity cc = new CSingleRunningCapacity(c);
        ReconfigurationPlan plan = new DefaultReconfigurationPlan(mo);
        Assert.assertTrue(cc.isSatisfied(plan));

        //Bad resulting configuration
        plan.add(new BootVM(vm2, n1, 1, 2));
        Assert.assertFalse(cc.isSatisfied(plan));
        c.setContinuous(true);
        Assert.assertFalse(cc.isSatisfied(plan));

        //bad initial configuration
        m.addRunningVM(vm2, n1);
        plan = new DefaultReconfigurationPlan(mo);
        Assert.assertFalse(cc.isSatisfied(plan));

        //The discrete fix
        plan.add(new SuspendVM(vm2, n1, n1, 1, 3));
        c.setContinuous(false);
        Assert.assertTrue(cc.isSatisfied(plan));
        c.setContinuous(true);
        Assert.assertFalse(cc.isSatisfied(plan));

        //Already satisfied && continuous satisfaction
        m.addSleepingVM(vm2, n1);
        plan = new DefaultReconfigurationPlan(mo);
        plan.add(new ShutdownVM(vm1, n1, 0, 1));
        plan.add(new ResumeVM(vm2, n1, n1, 1, 2));
        c.setContinuous(true);
        Assert.assertTrue(cc.isSatisfied(plan));
    }
}
