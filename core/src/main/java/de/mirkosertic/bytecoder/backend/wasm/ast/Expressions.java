/*
 * Copyright 2018 Mirko Sertic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mirkosertic.bytecoder.backend.wasm.ast;

import java.util.List;

public class Expressions {

    public class I32 {

        public I32IF iff(final I32Condition condition) {
            final I32IF elem = new I32IF(parent, condition);
            parent.addChild(elem);
            return elem;
        }

        public I32IF iffeq(final Value leftValue, final Value rightValue) {
            final I32Condition condition = ConstExpressions.i32.eq(leftValue, rightValue);
            return iff(condition);
        }

    }

    private final Container parent;
    public final I32 i32;

    Expressions(final Container parent) {
        this.parent = parent;
        this.i32 = new I32();
    }

    public void voidCall(final Function function, final List<Value> arguments) {
        final Call call = new Call(function, arguments);
        parent.addChild(call);
    }

    public Block block(final String label) {
        final Block block = new Block(label, parent);
        parent.addChild(block);
        return block;
    }

    public void branchOutOf(final Block surroundingBlock) {
        final Branch branch = new Branch(surroundingBlock);
        parent.addChild(branch);
    }

    public void branchOutIff(final Block block, final Value condition) {
        final BranchIf branch = new BranchIf(block, condition);
        parent.addChild(branch);
    }

    public void ret(final Value value) {
        parent.addChild(new Return(value));
    }

    public void ret() {
        parent.addChild(new Return());
    }

    public void unreachable() {
        parent.addChild(new Unreachable());
    }

    public void setLocal(final Local local, final Value value) {
        final SetLocal setLocal = new SetLocal(local, value);
        parent.addChild(setLocal);
    }

    public void setGlobal(final Global global, final Value value) {
        final SetGlobal setGlobal = new SetGlobal(global, value);
        parent.addChild(setGlobal);
    }
}