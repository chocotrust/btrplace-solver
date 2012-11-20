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

import btrplace.model.Mapping;
import btrplace.model.Model;
import btrplace.plan.Action;
import btrplace.plan.DefaultReconfigurationPlan;
import btrplace.plan.ReconfigurationPlan;
import btrplace.plan.SolverException;
import btrplace.solver.choco.actionModel.*;
import choco.cp.solver.CPSolver;
import choco.kernel.solver.variables.integer.IntDomainVar;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.*;

/**
 * Default implementation of {@link ReconfigurationProblem}.
 * TODO: resource capacity
 * TODO: actions model
 *
 * @author Fabien Hermenier
 */
public class DefaultReconfigurationProblem implements ReconfigurationProblem {

    /**
     * The maximum duration of a plan in seconds: One hour.
     */
    public Integer DEFAULT_MAX_TIME = 3600;


    private Model model;

    private CPSolver solver;

    private Set<UUID> waitings;
    private Set<UUID> runnings;
    private Set<UUID> sleepings;
    private Set<UUID> destroyed;

    private UUID[] vms;
    private TObjectIntHashMap<UUID> revVMs;

    private UUID[] nodes;
    private TObjectIntHashMap<UUID> revNodes;

    private IntDomainVar start;
    private IntDomainVar end;

    private ActionModel[] vmActions;
    private ActionModel[] nodeActions;

    private List<Slice> dSlices;
    private List<Slice> cSlices;

    private DurationEvaluators durEval;

    /**
     * Make a new ReconfigurationProblem that does not change the current state of the VMs
     * and rely on the default {@link DurationEvaluators}.
     *
     * @param m the initial model
     * @throws SolverException if an error occurred
     */
    public DefaultReconfigurationProblem(Model m) throws SolverException {
        this(m, m.getMapping().getWaitingVMs(),
                m.getMapping().getRunningVMs(),
                m.getMapping().getSleepingVMs(),
                new HashSet<UUID>());
    }

    /**
     * Make a new RP that does not change the current state of the VMs.
     *
     * @param m     the initial model
     * @param dEval the evaluator to use to evaluate the actions duration
     * @throws SolverException
     */
    public DefaultReconfigurationProblem(Model m, DurationEvaluators dEval) throws SolverException {
        this(m, dEval,
                m.getMapping().getWaitingVMs(),
                m.getMapping().getRunningVMs(),
                m.getMapping().getSleepingVMs(),
                new HashSet<UUID>());
    }

    public DefaultReconfigurationProblem(Model m,
                                         Set<UUID> toWait,
                                         Set<UUID> toRun,
                                         Set<UUID> toSleep,
                                         Set<UUID> toDestroy) throws SolverException {
        this(m, new DurationEvaluators(), toWait, toRun, toSleep, toDestroy);
    }

    public DefaultReconfigurationProblem(Model m,
                                         DurationEvaluators dEval,
                                         Set<UUID> toWait,
                                         Set<UUID> toRun,
                                         Set<UUID> toSleep,
                                         Set<UUID> toDestroy) throws SolverException {
        waitings = toWait;
        runnings = toRun;
        sleepings = toSleep;
        destroyed = toDestroy;
        model = m;
        durEval = dEval;

        cSlices = new ArrayList<Slice>();
        dSlices = new ArrayList<Slice>();

        solver = new CPSolver();
        start = solver.makeConstantIntVar(0);
        end = solver.createBoundIntVar("end", 0, DEFAULT_MAX_TIME);

        solver.post(solver.geq(end, start));

        fillElements();

        makeNodeActionModels();
        makeVMActionModels();


    }

    private void fillElements() throws SolverException {

        Set<UUID> allVMs = new HashSet<UUID>(model.getMapping().getAllVMs());
        allVMs.addAll(waitings); //The only VMs that may not appear in the mapping

        vms = new UUID[allVMs.size()];
        revVMs = new TObjectIntHashMap<UUID>(allVMs.size(), 0.5f, -1); //0.5f is the default load factor

        int i = 0;
        for (UUID vm : allVMs) {
            vms[i] = vm;
            revVMs.put(vm, i++);
        }

        nodes = new UUID[model.getMapping().getAllNodes().size()];
        revNodes = new TObjectIntHashMap<UUID>(nodes.length, 0.5f, -1);
        i = 0;
        for (UUID nId : model.getMapping().getAllNodes()) {
            nodes[i] = nId;
            revNodes.put(nId, i++);
        }
    }

    private void makeVMActionModels() throws SolverException {
        Mapping map = model.getMapping();
        vmActions = new ActionModel[vms.length];
        for (int i = 0; i < vms.length; i++) {
            UUID vmId = vms[i];
            if (runnings.contains(vmId)) {
                if (map.getSleepingVMs().contains(vmId)) {
                    vmActions[i] = new ResumeVMModel(this, vmId);
                } else if (map.getRunningVMs().contains(vmId)) {
                    vmActions[i] = new RelocatableVMModel(this, vmId);
                } else if (map.getWaitingVMs().contains(vmId)) {
                    vmActions[i] = new BootVMModel(this, vmId);
                } else {
                    throw new SolverException(model, "Unable to set VM '" + vmId + "' running: not instantiated");
                }
            }
            if (waitings.contains(vmId)) {
                if (vmActions[i] != null) {
                    throw new SolverException(model, "Next state for VM '" + vmId + "' is ambiguous");
                } else if (!map.getAllVMs().contains(vmId)) {
                    vmActions[i] = new InstantiateVMModel(this, vmId);
                } else if (map.getWaitingVMs().contains(vmId)) {
                    vmActions[i] = new StayAwayVMModel(this, vmId);
                } else {
                    throw new SolverException(model, "Unable to set VM '" + vmId + "' waiting: already instantiated or unknown");
                }
            }
            if (sleepings.contains(vmId)) {
                if (vmActions[i] != null) {
                    throw new SolverException(model, "Next state for VM '" + vmId + "' is ambiguous");
                } else if (map.getRunningVMs().contains(vmId)) {
                    vmActions[i] = new SuspendVMModel(this, vmId);
                } else if (map.getSleepingVMs().contains(vmId)) {
                    vmActions[i] = new StayAwayVMModel(this, vmId);
                } else {
                    throw new SolverException(model, "Unable to set VM '" + vmId + "' sleeping: should be running");
                }
            }
            if (destroyed.contains(vmId)) {
                if (vmActions[i] != null) {
                    throw new SolverException(model, "Next state for VM '" + vmId + "' is ambiguous");
                } else if (map.getRunningVMs().contains(vmId)) {
                    vmActions[i] = new ShutdownVMModel(this, vmId);
                } else {
                    throw new SolverException(model, "Unable to halt VM '" + vmId + "': should be running");
                }
            }
            if (vmActions[i] == null) {
                throw new SolverException(model, "Next state for VM '" + vmId + "' is undefined");
            }
            Slice s = vmActions[i].getCSlice();
            if (s != null) {
                cSlices.add(s);
            }

            s = vmActions[i].getDSlice();
            if (s != null) {
                dSlices.add(s);
            }
        }
    }

    private void makeNodeActionModels() throws SolverException {

        Mapping m = model.getMapping();
        nodeActions = new ActionModel[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            UUID nId = nodes[i];
            if (m.getOfflineNodes().contains(nId)) {
                nodeActions[i] = new BootableNodeModel(this, nId);
            }
            if (m.getOnlineNodes().contains(nId)) {
                if (nodeActions[i] != null) {
                    throw new SolverException(model, "Next state for node '" + nId + "' is ambiguous");
                }
                nodeActions[i] = new ShutdownableNodeModel(this, nId);
            }

            Slice s = nodeActions[i].getCSlice();
            if (s != null) {
                cSlices.add(s);
            }

            s = nodeActions[i].getDSlice();
            if (s != null) {
                dSlices.add(s);
            }
        }
    }

    @Override
    public int getCurrentVMLocation(int vmIdx) {
        UUID id = getVM(vmIdx);
        if (id == null) {
            return -1;
        }
        UUID nodeId = model.getMapping().getVMLocation(id);
        return nodeId == null ? -1 : getNode(nodeId);
    }

    @Override
    public UUID[] getNodes() {
        return nodes;
    }

    @Override
    public UUID[] getVMs() {
        return vms;
    }

    @Override
    public Model getSourceModel() {
        return model;
    }

    @Override
    public Set<UUID> getFutureRunningVMs() {
        return runnings;
    }

    @Override
    public Set<UUID> getFutureWaitingVMs() {
        return waitings;
    }

    @Override
    public Set<UUID> getFutureSleepingVMs() {
        return sleepings;
    }

    @Override
    public Set<UUID> getFutureDestroyedVMs() {
        return destroyed;
    }

    @Override
    public IntDomainVar getStart() {
        return start;
    }

    @Override
    public IntDomainVar getEnd() {
        return end;
    }

    @Override
    public int getVM(UUID vm) {
        return revVMs.get(vm);
    }

    @Override
    public UUID getVM(int idx) {
        return vms[idx];
    }

    @Override
    public int getNode(UUID n) {
        return revNodes.get(n);
    }

    @Override
    public UUID getNode(int idx) {
        return nodes[idx];
    }

    @Override
    public ActionModel[] getVMActions() {
        return vmActions;
    }

    @Override
    public ActionModel[] getVMActions(Set<UUID> id) {
        return vmActions;
    }

    @Override
    public ActionModel[] getNodeActions() {
        return nodeActions;
    }

    @Override
    public DurationEvaluators getDurationEvaluator() {
        return durEval;
    }

    @Override
    public List<Slice> getDSlices() {
        return dSlices;
    }

    @Override
    public List<Slice> getCSlices() {
        return cSlices;
    }

    @Override
    public ReconfigurationPlan extractSolution() {
        if (!Boolean.TRUE.equals(solver.isFeasible())) {
            return null;
        }

        DefaultReconfigurationPlan plan = new DefaultReconfigurationPlan(model);
        for (ActionModel action : nodeActions) {
            for (Action a : action.getResultingActions(this)) {
                plan.add(a);
            }

        }
        for (ActionModel action : vmActions) {
            for (Action a : action.getResultingActions(this)) {
                plan.add(a);
            }

        }
        assert checkConsistency(plan);
        return plan;
    }

    private boolean checkConsistency(ReconfigurationPlan p) {
        for (Action a : p) {
            if (a.getStart() == a.getEnd()) {
                return false;
            }
        }
        return p.getDuration() == end.getVal();
    }

    @Override
    public CPSolver getSolver() {
        return solver;
    }

    @Override
    public IntDomainVar makeHostVariable(String n) {
        return solver.createEnumIntVar(n, 0, nodes.length - 1);
    }

    @Override
    public IntDomainVar makeCurrentHost(String n, UUID vmId) throws SolverException {
        int idx = getVM(vmId);
        if (idx < 0) {
            throw new SolverException(model, "Unknown VM '" + vmId + "'");
        }
        return makeCurrentNode(n, model.getMapping().getVMLocation(vmId));
    }

    @Override
    public IntDomainVar makeCurrentNode(String n, UUID nId) throws SolverException {
        int idx = getNode(nId);
        if (idx < 0) {
            throw new SolverException(model, "Unknown node '" + nId + "'");
        }
        return solver.makeConstantIntVar(n, idx);
    }

    @Override
    public IntDomainVar makeDuration(String n) {
        return solver.createBoundIntVar(n, 0, end.getSup());
    }

    @Override
    public IntDomainVar makeDuration(String n, int lb, int ub) throws SolverException {
        if (lb < 0 || ub < lb) {
            throw new SolverException(model, "Unable to create duration '" + n + "': invalid bounds");
        }
        return solver.createBoundIntVar(n, lb, ub < end.getSup() ? ub : end.getSup());
    }
}
