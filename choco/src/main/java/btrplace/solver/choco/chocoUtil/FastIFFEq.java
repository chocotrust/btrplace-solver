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

import choco.cp.solver.variables.integer.IntVarEvent;
import choco.kernel.solver.ContradictionException;
import choco.kernel.solver.SolverException;
import choco.kernel.solver.constraints.integer.AbstractBinIntSConstraint;
import choco.kernel.solver.variables.integer.IntDomainVar;

/**
 * A fast implementation for BVAR => VAR = CSTE
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 29/06/11
 */
public class FastIFFEq extends AbstractBinIntSConstraint {

    private final int constant;

    public FastIFFEq(IntDomainVar b, IntDomainVar var, int constante) {
        super(b, var);
        if (!b.hasBooleanDomain()) {
            throw new SolverException(b.getName() + " is not a boolean variable");
        }
        this.constant = constante;
    }

    @Override
    public int getFilteredEventMask(int idx) {
        if (idx == 0) {
            return IntVarEvent.INSTINT_MASK;
        } else {
            if (v1.hasEnumeratedDomain()) {
                return IntVarEvent.INSTINT_MASK + IntVarEvent.REMVAL_MASK;
            }
            return IntVarEvent.INSTINT_MASK + IntVarEvent.BOUNDS_MASK;
        }
    }

    @Override
    public void propagate() throws ContradictionException {
        if (v0.isInstantiated()) {
            int val = v0.getVal();
            if (val == 0) {
                if (v1.removeVal(constant, this, false)) {
                    this.setEntailed();
                }
            } else {
                v1.instantiate(constant, this, false);
                this.setEntailed();
            }
        }
        if (v1.isInstantiatedTo(constant)) {
            v0.instantiate(1, this, false);
        } else if (!v1.canBeInstantiatedTo(constant)) {
            v0.instantiate(0, this, false);
            this.setEntailed();
        }
    }

    @Override
    public void awakeOnInst(int idx) throws ContradictionException {
        if (idx == 0) {
            int val = v0.getVal();
            if (val == 0) {
                if (v1.removeVal(constant, this, false)) {
                    this.setEntailed();
                }
            } else {
                v1.instantiate(constant, this, false);
            }
        } else {
            if (v1.isInstantiatedTo(constant)) {
                v0.instantiate(1, this, false);
            } else {
                v0.instantiate(0, this, false);
            }
        }
    }

    @Override
    public void awakeOnRem(int varIdx, int val) throws ContradictionException {
        if (varIdx == 1 && val == constant) {
            v0.instantiate(0, this, false);
        }
    }

    @Override
    public void awakeOnInf(int varIdx) throws ContradictionException {
        if (varIdx == 1) {
            if (!v1.canBeInstantiatedTo(constant)) {
                v0.instantiate(0, this, false);
                this.setEntailed();
            }
        }
    }

    @Override
    public void awakeOnSup(int varIdx) throws ContradictionException {
        if (varIdx == 1) {
            if (!v1.canBeInstantiatedTo(constant)) {
                v0.instantiate(0, this, false);
                this.setEntailed();
            }
        }
    }

    @Override
    public boolean isSatisfied(int[] tuple) {
        return (tuple[0] == 1 && tuple[1] == constant)
                || (tuple[0] == 0 && tuple[1] != constant);
    }

    @Override
    public boolean isConsistent() {
        if (vars[0].isInstantiated() || vars[1].isInstantiated()) {
            return ((vars[0].isInstantiatedTo(0) && !vars[1].isInstantiatedTo(constant))
                    || (vars[0].isInstantiatedTo(1) && vars[1].isInstantiatedTo(constant)));
        }
        return true;
    }
}
