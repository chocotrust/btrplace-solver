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

import btrplace.model.Model;
import btrplace.plan.Action;
import btrplace.plan.VMEvent;

import java.util.UUID;

/**
 * An action to indicate the amount of resource of a given type
 * to allocate to a VM.
 *
 * @author Fabien Hermenier
 */
public class Allocate extends Action implements VMEvent {

    private UUID node;

    private AllocateEvent ev;

    /**
     * Make a new constraint.
     *
     * @param vm     the VM identifier
     * @param host   the identifier of the node hosting the VM
     * @param rcId   the resource identifier
     * @param amount the minimum amount of resource to allocate
     * @param st     the moment the action starts
     * @param ed     the moment the action ends
     */
    public Allocate(UUID vm, UUID host, String rcId, int amount, int st, int ed) {
        super(st, ed);
        ev = new AllocateEvent(vm, rcId, amount);
        this.node = host;
    }

    /**
     * Get the node that is currently hosting the VM.
     *
     * @return the node identifier
     */
    public UUID getHost() {
        return node;
    }

    @Override
    public UUID getVM() {
        return ev.getVM();
    }

    /**
     * Get the resource identifier.
     *
     * @return a non-empty string
     */
    public String getResourceId() {
        return ev.getResourceId();
    }

    /**
     * Get the amount of resources to allocate to the VM.
     *
     * @return a positive number
     */
    public int getAmount() {
        return ev.getAmount();
    }

    @Override
    public boolean applyAction(Model i) {
        return ev.apply(i);
    }

    @Override
    public String pretty() {
        return new StringBuilder("allocate(")
                .append("vm=").append(ev.getVM())
                .append(", on=").append(node)
                .append(", rc=").append(ev.getResourceId())
                .append(", amount=").append(ev.getAmount())
                .append(')').toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if (o == this) {
            return true;
        } else if (o.getClass() == this.getClass()) {
            Allocate that = (Allocate) o;
            return this.getVM().equals(that.getVM())
                    && this.node.equals(that.node)
                    && this.getResourceId().equals(that.getResourceId())
                    && this.getStart() == that.getStart()
                    && this.getEnd() == that.getEnd()
                    && this.getAmount() == that.getAmount();
        }
        return false;
    }

    @Override
    public int hashCode() {
        int res = getEnd();
        res = getStart() + 31 * res;
        res = res * 31 + ev.getAmount();
        res = res * 31 + ev.getResourceId().hashCode();
        res = res * 31 + ev.getVM().hashCode();
        res = res + 31 + node.hashCode();
        return res;
    }

    @Override
    public Object visit(ActionVisitor v) {
        return v.visit(this);
    }
}
