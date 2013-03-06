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

package btrplace.solver.choco.chocoUtil;

import choco.kernel.common.logging.ChocoLogging;
import choco.kernel.memory.IEnvironment;
import choco.kernel.memory.IStateInt;
import choco.kernel.memory.IStateIntVector;
import choco.kernel.solver.ContradictionException;
import choco.kernel.solver.variables.integer.IntDomainVar;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;

import java.util.Arrays;
import java.util.BitSet;

/**
 * TODO: Transpose dimension/element indexes to remove getUsage()
 *
 * @author Fabien Hermenier
 */
public class LocalTaskScheduler {

    private int me;

    /**
     * out[i] = true <=> the consuming slice i will leave me.
     */
    private BitSet out;

    /**
     * The moment the consuming slices ends. Same order as the hosting variables.
     */
    private IntDomainVar[] cEnds;

    private IStateIntVector vIn;

    /*
     * The moment the demanding slices ends. Same order as the hosting variables.
     */
    private IntDomainVar[] dStarts;

    private int[] startupFree;

    public static final int DEBUG = -5;

    private int[] associations;

    private int[] revAssociations;

    public static final int NO_ASSOCIATIONS = -1;

    private int[] sortedMinProfile;

    private TIntIntHashMap[] profilesMin;

    private TIntIntHashMap[] profilesMax;

    private int[] sortedMaxProfile;

    /**
     * LB of the moment the last c-slice leaves.
     */
    private IStateInt lastCendInf;

    /**
     * UB of the moment the last c-slice leaves.
     */
    private IStateInt lastCendSup;

    private int[][] capacities;

    private int[][] cUsages, dUsages;

    private int nbDims = 0;

    private IntDomainVar early, last;

    public LocalTaskScheduler(int me,
                              IEnvironment env,
                              IntDomainVar early,
                              IntDomainVar last,
                              int[][] capacities,
                              int[][] cUsages,
                              IntDomainVar[] cEnds,
                              BitSet outs,
                              int[][] dUsages,
                              IntDomainVar[] dStarts,
                              IStateIntVector vIn,
                              int[] assocs,
                              int[] revAssocs) {
        this.early = early;
        this.last = last;

        this.associations = assocs;
        this.me = me;
        this.cEnds = cEnds;

        this.capacities = capacities;
        this.nbDims = capacities.length;
        this.cUsages = cUsages;
        this.dUsages = dUsages;

        this.dStarts = dStarts;
        this.vIn = vIn;
        this.out = outs;
        revAssociations = revAssocs;

        //The amount of free resources at startup

        startupFree = new int[nbDims];
        profilesMax = new TIntIntHashMap[nbDims];
        profilesMin = new TIntIntHashMap[nbDims];
        for (int i = 0; i < capacities.length; i++) {
            startupFree[i] = capacities[i][me];
            profilesMax[i] = new TIntIntHashMap();
            profilesMin[i] = new TIntIntHashMap();
        }

        int lastInf = out.isEmpty() ? 0 : Integer.MAX_VALUE;
        int lastSup = 0;

        for (int j = out.nextSetBit(0); j >= 0; j = out.nextSetBit(j + 1)) {
            for (int i = 0; i < capacities.length; i++) {
                startupFree[i] -= cUsages[i][j];
            }

            int i = cEnds[j].getInf();
            int s = cEnds[j].getSup();
            if (i < lastInf) {
                lastInf = i;
            }
            if (s > lastSup) {
                lastSup = s;
            }
        }
        this.lastCendInf = env.makeInt(lastInf);
        this.lastCendSup = env.makeInt(lastSup);
    }

    public boolean propagate() throws ContradictionException {
        computeProfiles();
        if (!checkInvariant()) {
            return false;
        }
        updateCEndsSup();
        updateDStartsInf();
        updateDStartsSup();
        return true;
    }

    /**
     * Translation for a relatives resources changes to an absolute free resources.
     *
     * @param changes       the map that indicates the free CPU variation
     * @param sortedMoments the different moments sorted in ascending order
     */
    private static void toAbsoluteFreeResources(TIntIntHashMap changes, int[] sortedMoments) {
        for (int i = 1; i < sortedMoments.length; i++) {
            int t = sortedMoments[i];
            int lastT = sortedMoments[i - 1];
            int lastFree = changes.get(lastT);

            changes.put(t, changes.get(t) + lastFree);
        }
    }

    public void computeProfiles() {

        int lastInf = out.isEmpty() ? 0 : Integer.MAX_VALUE;
        int lastSup = 0;

        TIntArrayList idxToMoment = new TIntArrayList();
        TIntIntHashMap momentToIdx = new TIntIntHashMap();

        momentToIdx.put(0, 0);
        idxToMoment.add(0);
        int idx = 1;
        for (int j = out.nextSetBit(0); j >= 0; j = out.nextSetBit(j + 1)) {
            int t = cEnds[j].getInf();
            if (!momentToIdx.containsKey(t)) {
                momentToIdx.put(t, idx++);
                idxToMoment.add(t);
            }

            t = cEnds[j].getSup();
            if (!momentToIdx.containsKey(t)) {
                momentToIdx.put(t, idx++);
                idxToMoment.add(t);
            }
        }

        for (int x = 0; x < vIn.size(); x++) {
            int j = vIn.get(x);
            int t = dStarts[j].getSup();
            if (!momentToIdx.containsKey(t)) {
                momentToIdx.put(t, idx++);
                idxToMoment.add(t);
            }
            t = dStarts[j].getInf();
            if (!momentToIdx.containsKey(t)) {
                momentToIdx.put(t, idx++);
                idxToMoment.add(t);
            }
        }

        int [][] max = new int[nbDims][];
        int [][] min = new int[nbDims][];
        for (int i = 0; i < nbDims; i++) {
            max[i] = new int[idxToMoment.size()];
            min[i] = new int[idxToMoment.size()];
            min[i][0] = capacities[i][me] - startupFree[i];
            max[i][0] = capacities[i][me] - startupFree[i];

        }

        for (int j = out.nextSetBit(0); j >= 0; j = out.nextSetBit(j + 1)) {

            int t = cEnds[j].getInf();
            int tIdx = momentToIdx.get(t);
            if (t < lastInf) {
                lastInf = t;
            }

            if (associatedToDSliceOnCurrentNode(j) && increase(j, revAssociations[j])) {
                if (me == DEBUG || DEBUG == -2) {
                    ChocoLogging.getBranchingLogger().finest(me + " " + cEnds[j].pretty() + " increasing");
                }
                for (int i = 0; i < nbDims; i++) {
                    max[i][tIdx] -= cUsages[i][j];
                    //profilesMax[i].put(t, profilesMax[i].get(t) - cUsages[i][j]);
                }

            } else {
                if (me == DEBUG || DEBUG == -2) {
                    ChocoLogging.getBranchingLogger().finest(me + " " + cEnds[j].pretty() + " < or non-associated (" + (revAssociations[j] >= 0 ? dStarts[revAssociations[j]].pretty() : "no rev") + "?)");
                }
                for (int i = 0; i < nbDims; i++) {
                    min[i][tIdx] -= cUsages[i][j];
                    //profilesMin[i].put(t, profilesMin[i].get(t) - cUsages[i][j]);
                }

            }

            t = cEnds[j].getSup();
            tIdx = momentToIdx.get(t);
            if (t > lastSup) {
                lastSup = t;
            }
            if (associatedToDSliceOnCurrentNode(j) && increase(j, revAssociations[j])) {
                for (int i = 0; i < nbDims; i++) {
                    //profilesMin[i].put(t, profilesMin[i].get(t) - cUsages[i][j]);
                   min[i][tIdx] -= cUsages[i][j];
                }
            } else {
                for (int i = 0; i < nbDims; i++) {
                    //profilesMax[i].put(t, profilesMax[i].get(t) - cUsages[i][j]);
                    max[i][tIdx] -= cUsages[i][j];
                }
            }
        }
        if (out.isEmpty()) {
            lastInf = 0;
            lastSup = 0;
        }

        lastCendInf.set(lastInf);
        lastCendSup.set(lastSup);

        for (int i = 0; i < nbDims; i++) {
            for (int x = 0; x < vIn.size(); x++) {
                int j = vIn.get(x);
                int t = dStarts[j].getSup();
                int tIdx = momentToIdx.get(t);
                //profilesMin[i].put(t, profilesMin[i].get(t) + dUsages[i][j]);
                min[i][tIdx] += dUsages[i][j];
                t = dStarts[j].getInf();
                tIdx = momentToIdx.get(t);
                //profilesMax[i].put(t, profilesMax[i].get(t) + dUsages[i][j]);
                max[i][tIdx] += dUsages[i][j];
            }
        }

        for (int i = 0; i < nbDims; i++) {
            //What is necessarily used on the resource
            profilesMin[i].clear();

            //Maximum possible usage on the resource
            profilesMax[i].clear();

            for (int tIdx = 0; tIdx < idxToMoment.size(); tIdx++) {
                int t = idxToMoment.get(tIdx);
                profilesMin[i].put(t, min[i][tIdx]);
                profilesMax[i].put(t, max[i][tIdx]);
            }
        }

        //Now transforms into an absolute profile
        sortedMinProfile = null;
        sortedMinProfile = profilesMin[0].keys();
        Arrays.sort(sortedMinProfile);

        sortedMaxProfile = null;
        sortedMaxProfile = profilesMax[0].keys();
        for (int i = 0; i < nbDims; i++) {
            profilesMax[i].keys(sortedMaxProfile);
        }

        Arrays.sort(sortedMaxProfile);

        for (int i = 0; i < nbDims; i++) {
            toAbsoluteFreeResources(profilesMin[i], sortedMinProfile);
            toAbsoluteFreeResources(profilesMax[i], sortedMaxProfile);
        }

        if (me == DEBUG || DEBUG == -2) {
            ChocoLogging.getBranchingLogger().finest("---" + me + "--- startup=(" + Arrays.toString(startupFree) + ")"
                    + " init=(" + Arrays.toString(getUsages(capacities, me)) + "); early=" + early.pretty() + "; last=" + last.pretty());
            for (int x = 0; x < vIn.size(); x++) {
                int i = vIn.get(x);
                ChocoLogging.getBranchingLogger().finest((dStarts[i].isInstantiated() ? "!" : "?") + " " + dStarts[i].pretty() + " " + Arrays.toString(getUsages(dUsages, i)));
            }

            for (int i = out.nextSetBit(0); i >= 0; i = out.nextSetBit(i + 1)) {
                ChocoLogging.getBranchingLogger().finest((cEnds[i].isInstantiated() ? "!" : "?") + " " + cEnds[i].pretty() + " " + Arrays.toString(getUsages(cUsages, i)));
            }
            //ChocoLogging.getBranchingLogger().finest("---");


            for (int i = 0; i < nbDims; i++) {
                ChocoLogging.getBranchingLogger().finest("profileMin dim " + i + "=" + prettyProfile(sortedMinProfile, profilesMin[i]));
                ChocoLogging.getBranchingLogger().finest("profileMax dim " + i + "=" + prettyProfile(sortedMaxProfile, profilesMax[i]));
            }
        }
    }

    private boolean increase(int x, int y) {
        for (int i = 0; i < nbDims; i++) {
            if (dUsages[i][y] > cUsages[i][x]) {
                return true;
            }
        }
        return false;
    }

    private boolean associatedToDSliceOnCurrentNode(int cSlice) {
        return revAssociations[cSlice] != NO_ASSOCIATIONS && isIn(revAssociations[cSlice]);
    }

    private boolean isIn(int idx) {

        for (int x = 0; x < vIn.size(); x++) {
            int i = vIn.get(x);
            if (i == idx) {
                return true;
            }
        }
        return false;
    }

    private boolean associatedToCSliceOnCurrentNode(int dSlice) {
        return associations[dSlice] != NO_ASSOCIATIONS && out.get(associations[dSlice]);
    }

    private String prettyProfile(int[] ascMoments, TIntIntHashMap prof) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ascMoments.length; i++) {
            int t = ascMoments[i];
            b.append(t);
            b.append(":(");
            b.append(prof.get(t));
            b.append(",");
            b.append(prof.get(t));
            b.append(")");
            if (i != ascMoments.length - 1) {
                b.append(" ");
            }
        }
        return b.toString();
    }

    public boolean checkInvariant() {
        for (int x = 0; x < sortedMinProfile.length; x++) {
            int t = sortedMinProfile[x];
            for (int i = 0; i < nbDims; i++) {
                if (profilesMin[i].get(t) > capacities[i][me]) {
                    if (me == DEBUG || DEBUG == -2) {
                        ChocoLogging.getBranchingLogger().info("(" + me + ") Invalid min profile at " + t + " on dimension " + i
                                + ": " + profilesMin[i].get(t) + " > " + capacities[i][me]);
                    }
                    return false;
                }
            }
        }

        //invariant related to the last and the early.
        for (int idx = 0; idx < vIn.size(); idx++) {
            int i = vIn.get(idx);
            if (dStarts[i].getSup() < early.getSup()) {
                if (me == DEBUG || DEBUG == -2) {
                    ChocoLogging.getBranchingLogger().info("(" + me + ") The dSlice " + i + " has to start too early (max is " + dStarts[i].pretty() + ") (min expected=" + early.pretty() + ")");
                }
                return false;
            }
        }

        for (int i = out.nextSetBit(0); i >= 0; i = out.nextSetBit(i + 1)) {
            if (cEnds[i].getInf() > last.getSup()) {
                if (me == DEBUG || DEBUG == -2) {
                    ChocoLogging.getBranchingLogger().info("(" + me + ") The cSlice " + i + " has to end too late (last expected=" + last.getSup() + ")");
                }
                return false;
            }

        }
        return true;
    }

    private void updateDStartsInf() throws ContradictionException {

        for (int idx = 0; idx < vIn.size(); idx++) {
            int i = vIn.get(idx);
            if (!dStarts[i].isInstantiated() && !associatedToCSliceOnCurrentNode(i)) {

                int[] myUsage = getUsages(dUsages, i);

                int lastT = -1;
                for (int x = sortedMinProfile.length - 1; x >= 0; x--) {
                    int t = sortedMinProfile[x];
                    if (t <= dStarts[i].getInf()) {
                        break;
                    }
                    int prevT = sortedMinProfile[x - 1];
                    if (t <= dStarts[i].getSup()
                            && exceedCapacity(profilesMin, prevT, myUsage)) {
                        lastT = t;
                        break;
                    }
                }
                if (lastT != -1) {
                    dStarts[i].setInf(Math.max(lastT, early.getInf()));
                }
            }
        }
    }

    private void updateDStartsSup() throws ContradictionException {


        int[] myCapacity = getUsages(capacities, me);
        int lastSup = -1;
        for (int i = sortedMaxProfile.length - 1; i >= 0; i--) {
            int t = sortedMaxProfile[i];
            //if (profileMaxCPU.get(t) <= capacityCPU && profileMaxMem.get(t) <= capacityMem) {
            if (!exceedCapacity(profilesMax, t, myCapacity)) {
                lastSup = t;
            } else {
                break;
            }
        }
        /*if (me == DEBUG || DEBUG == -2) {
            ChocoLogging.getBranchingLogger().finest(me + ": lastSup=" + lastSup);
        } */
        if (lastSup != -1) {
            for (int x = 0; x < vIn.size(); x++) {
                int i = vIn.get(x);
                if (!dStarts[i].isInstantiated() && !associatedToCSliceOnCurrentNode(i) && dStarts[i].getSup() > lastSup) {
                    int s = Math.max(dStarts[i].getInf(), lastSup);
                    dStarts[i].setSup(Math.max(s, early.getSup()));
                }
            }
        }
    }

    private void updateCEndsSup() throws ContradictionException {
        for (int i = out.nextSetBit(0); i >= 0; i = out.nextSetBit(i + 1)) {
            if (!cEnds[i].isInstantiated() && !associatedToDSliceOnCurrentNode(i)) {

                int[] myUsage = getUsages(cUsages, i);
                int lastT = -1;
                for (int x = 0; x < sortedMinProfile.length; x++) {
                    int t = sortedMinProfile[x];
                    if (t >= cEnds[i].getSup()) {
                        break;
                    } else if (t >= cEnds[i].getInf() &&
                            exceedCapacity(profilesMin, t, myUsage)) {
                        lastT = t;
                        break;
                    }
                }
                if (lastT != -1) {
                    cEnds[i].setSup(Math.min(lastT, last.getSup()));
                }

            }
        }
    }

    private boolean exceedCapacity(TIntIntHashMap[] profiles, int t, int[] usage) {
        for (int i = 0; i < nbDims; i++) {
            if (profiles[i].get(t) + usage[i] > capacities[i][me]) {
                return true;
            }
        }
        return false;
    }

    private int[] getUsages(int[][] usages, int i) {
        int[] u = new int[nbDims];
        for (int x = 0; x < nbDims; x++) {
            u[x] = usages[x][i];
        }
        return u;
    }
}
