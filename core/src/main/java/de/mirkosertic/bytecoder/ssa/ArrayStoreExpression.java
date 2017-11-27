/*
 * Copyright 2017 Mirko Sertic
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
package de.mirkosertic.bytecoder.ssa;

public class ArrayStoreExpression extends Expression {

    private final Type arrayType;
    private final Variable array;
    private final Variable index;
    private final Variable value;

    public ArrayStoreExpression(Type aArrayType, Variable aArray, Variable aIndex, Variable aValue) {
        arrayType = aArrayType;
        array = aArray;
        index = aIndex;
        value = aValue;
    }

    public Type getArrayType() {
        return arrayType;
    }

    public Variable getArray() {
        return array;
    }

    public Variable getIndex() {
        return index;
    }

    public Variable getValue() {
        return value;
    }
}
