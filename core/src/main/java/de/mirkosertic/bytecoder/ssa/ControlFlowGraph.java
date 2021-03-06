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

import de.mirkosertic.bytecoder.core.BytecodeExceptionTableEntry;
import de.mirkosertic.bytecoder.core.BytecodeOpcodeAddress;
import de.mirkosertic.bytecoder.core.BytecodeProgram;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class ControlFlowGraph {

    private final List<RegionNode> dominatedNodes;
    private final List<RegionNode> knownNodes;
    private final Program program;

    public ControlFlowGraph(final Program aProgram) {
        program = aProgram;
        dominatedNodes = new ArrayList<>();
        knownNodes = new ArrayList<>();
    }

    public Program getProgram() {
        return program;
    }

    public Set<RegionNode> finalNodes() {
        final Set<RegionNode> theNodes = new HashSet<>();
        for (final RegionNode theNode : knownNodes) {
            final Set<RegionNode> theSuccessors = new HashSet<>();
            for (final Map.Entry<RegionNode.Edge, RegionNode> theSuccessor : theNode.getSuccessors().entrySet()) {
                if (theSuccessor.getKey().getType() == RegionNode.EdgeType.NORMAL) {
                    theSuccessors.add(theSuccessor.getValue());
                }
            }
            if (theSuccessors.isEmpty()) {
                theNodes.add(theNode);
            }
        }
        return theNodes;
    }

    public void calculateReachabilityAndMarkBackEdges() {
        calculateReachabilityAndMarkBackEdges(new GraphNodePath(), startNode());
        // We have to do the same for finally nodes
        for (final RegionNode theNode : knownNodes) {
            if (theNode.getType() == RegionNode.BlockType.FINALLY) {
                calculateReachabilityAndMarkBackEdges(new GraphNodePath(), theNode);
            }
        }
    }

    private void calculateReachabilityAndMarkBackEdges(final GraphNodePath aPath, final RegionNode aNode) {
        aNode.addReachablePath(aPath);
        for (final Map.Entry<RegionNode.Edge, RegionNode> theEdge : aNode.getSuccessors().entrySet()) {
            final GraphNodePath theChildPath = new GraphNodePath(aPath);
            theChildPath.addToPath(aNode);
            if (aPath.contains(theEdge.getValue())) {
                // This is a back edge
                theEdge.getKey().changeTo(RegionNode.EdgeType.BACK);
                theEdge.getValue().addReachablePath(theChildPath);
                // We have already visited the back edge, so we do not to continue here
                // As this would lead to an endless loop
            } else {
                // Normal edge
                // Continue with graph traversal
                calculateReachabilityAndMarkBackEdges(theChildPath, theEdge.getValue());
            }
        }
    }

    public RegionNode createAt(final BytecodeOpcodeAddress aAddress, final RegionNode.BlockType aType) {
        final RegionNode theNewBlock = new RegionNode(this, aType, program, aAddress);
        addDominatedNode(theNewBlock);
        return theNewBlock;
    }

    public void addDominatedNode(final RegionNode aGraphNode) {
        dominatedNodes.add(aGraphNode);
        knownNodes.add(aGraphNode);
    }

    public RegionNode startNode() {
        return nodeStartingAt(BytecodeOpcodeAddress.START_AT_ZERO);
    }

    public RegionNode nodeStartingAt(final BytecodeOpcodeAddress aAddress) {
        for (final RegionNode theBlock : knownNodes) {
            if (Objects.equals(aAddress, theBlock.getStartAddress())) {
                return theBlock;
            }
        }
        throw new IllegalArgumentException("Unknown address : " + aAddress.getAddress());
    }

    public List<RegionNode> getKnownNodes() {
        return new ArrayList<>(knownNodes);
    }

    public List<RegionNode.ExceptionHandler> exceptionHandlersStartingAt(final BytecodeOpcodeAddress aAddress) {
        final List<RegionNode.ExceptionHandler> theHandler = new ArrayList<>();
        final BytecodeProgram.FlowInformation theFlowinfo = program.getFlowInformation();
        if (theFlowinfo != null) {
            final BytecodeProgram theBytecode = theFlowinfo.getProgram();
            for (final BytecodeExceptionTableEntry theEntry : theBytecode.getExceptionHandlers()) {
                if (theEntry.getStartPC().equals(aAddress) && !theEntry.isFinally()) {
                    RegionNode.ExceptionHandler theMatchingHandler = null;
                    for (final RegionNode.ExceptionHandler theExisting : theHandler) {
                        if (theExisting.regionMatchesTo(theEntry)) {
                            theMatchingHandler = theExisting;
                        }
                    }
                    if (theMatchingHandler == null) {
                        theMatchingHandler = new RegionNode.ExceptionHandler(theEntry.getStartPC(), theEntry.getEndPc());
                        theHandler.add(theMatchingHandler);
                    }

                    theMatchingHandler.addCatchEntry(theEntry);
                }
            }
        }
        theHandler.sort((o1, o2) -> Integer.compare(o2.getEndPC().getAddress(),  o1.getEndPC().getAddress()));
        return theHandler;
    }

    private static class IDRegister {

        private final List<Object> objects;

        public IDRegister() {
            objects = new ArrayList<>();
        }

        public String idFor(final Object aObject) {
            if (!objects.contains(aObject)) {
                objects.add(aObject);
            }
            return "" + objects.indexOf(aObject);
        }
    }

    static class DotContext {
        final RegionNode regionNode;
        final ExpressionList expressionList;
        final String owningNodeName;

        public DotContext(final RegionNode aRegionNode, final ExpressionList aExpressionList, final String aOwningNodeName) {
            regionNode = aRegionNode;
            expressionList = aExpressionList;
            owningNodeName = aOwningNodeName;
        }
    }

    class DotJump {
        final String source;
        final String target;
        final boolean backEdge;

        public DotJump(final String aSource, final String aTarget, final boolean aBackEdge) {
            source = aSource;
            target = aTarget;
            backEdge = aBackEdge;
        }
    }

    public String toDOT() {

        final IDRegister theRegister = new IDRegister();
        final List<DotJump> theJumps = new ArrayList<>();

        final StringWriter theStr = new StringWriter();
        try (final PrintWriter thePW = new PrintWriter(theStr)) {

            final Set<Value> theAllValues = new HashSet<>();

            thePW.println("digraph CFG {");

            final Consumer<DotContext> theExpressionConsumer = new Consumer<DotContext>() {

                @Override
                public void accept(final DotContext aContext) {
                    final List<Expression> theExpressios = aContext.expressionList.toList();
                    for (final Expression theExpression : theExpressios) {

                        theAllValues.add(theExpression);

                        final String theNodeName = theRegister.idFor(theExpression);

                        thePW.print("       ");
                        thePW.print(aContext.owningNodeName);
                        thePW.print(" -> E_");
                        thePW.print(theNodeName);
                        thePW.print("[style=dotted,color=blue,label=\"e-");
                        thePW.print(theExpressios.indexOf(theExpression));
                        thePW.println("\"];");

                        printIncomingValues(theExpression, "E_" + theNodeName);

                        if (theExpression instanceof GotoExpression) {
                            final GotoExpression theGoto = (GotoExpression) theExpression;
                            final RegionNode theJumpTarget = nodeStartingAt(theGoto.getJumpTarget());

                            final String theJumpTargetRegion = theRegister.idFor(theJumpTarget);

                            theJumps.add(new DotJump("E_" + theNodeName,
                                    "C_" + theJumpTargetRegion + "_control",
                                    aContext.regionNode.hasBackEdgeTo(theJumpTarget)));
                        }

                        if (theExpression instanceof ExpressionListContainer) {
                            final ExpressionListContainer theList = (ExpressionListContainer) theExpression;
                            for (final ExpressionList theCont : theList.getExpressionLists()) {
                                final DotContext theContext = new DotContext(aContext.regionNode, theCont, "E_" + theNodeName);
                                accept(theContext);
                            }
                        }
                    }
                }

                private void printIncomingValues(final Value aValue, final String aReceivingNodeID) {
                    if (aValue instanceof VariableAssignmentExpression) {
                        final VariableAssignmentExpression theAssignment = (VariableAssignmentExpression) aValue;
                        final Variable theTarget = theAssignment.getVariable();
                        theAllValues.add(theTarget);

                        thePW.print("       ");
                        thePW.print(aReceivingNodeID);
                        thePW.print(" -> V_");
                        thePW.print(theRegister.idFor(theTarget));
                        thePW.println(";");

                        final Value theValue = theAssignment.getValue();
                        theAllValues.add(theValue);

                        String theValueID = null;
                        if (theValue instanceof Expression) {
                            theValueID = "E_" + theRegister.idFor(theValue);
                        }
                        if (theValue instanceof PrimitiveValue) {
                            theValueID = "P_" + theRegister.idFor(theValue);
                        }
                        if (theValue instanceof Variable) {
                            theValueID = "V_" + theRegister.idFor(theValue);
                        }

                        thePW.print("       ");
                        thePW.print(theValueID);
                        thePW.print(" -> ");
                        thePW.print(aReceivingNodeID);
                        thePW.println(";");

                        if (theValue instanceof Expression) {
                            printIncomingValues(theValue, theValueID);
                        }

                    } else {
                        final List<Value> theIncomingDataFlows = aValue.incomingDataFlows();
                        for (final Value theValue : theIncomingDataFlows) {
                            theAllValues.add(theValue);

                            String theValueID = null;
                            if (theValue instanceof Expression) {
                                theValueID = "E_" + theRegister.idFor(theValue);
                            }
                            if (theValue instanceof PrimitiveValue) {
                                theValueID = "P_" + theRegister.idFor(theValue);
                            }
                            if (theValue instanceof Variable) {
                                theValueID = "V_" + theRegister.idFor(theValue);
                            }

                            thePW.print("       ");

                            thePW.print(theValueID);

                            thePW.print(" -> ");
                            thePW.print(aReceivingNodeID);
                            thePW.println(";");

                            if (theValue instanceof Expression) {
                                printIncomingValues(theValue, theValueID);
                            }
                        }
                    }
                }
            };

            for (final RegionNode theRegion : knownNodes) {

                final String theRegionID = theRegister.idFor(theRegion);

                thePW.print("   subgraph cluster_");
                thePW.print(theRegionID);
                thePW.println(" {");


                thePW.print("       label=\"Region Address ");
                thePW.print(theRegion.getStartAddress().getAddress());
                thePW.println("\";");

                thePW.println("       style=filled;");

                switch (theRegion.getType()) {
                    case NORMAL:
                        thePW.println("       color=lightgray;");
                        break;
                    case EXCEPTION_HANDLER:
                        thePW.println("       color=lightsalmon;");
                        break;
                    case FINALLY:
                        thePW.println("       color=goldenrod;");
                        break;
                }

                thePW.println();

                thePW.print("       C_");
                thePW.print(theRegionID);
                thePW.println("_control [shape=box, label=\"Control\"];");

                final DotContext theContext = new DotContext(theRegion, theRegion.getExpressions(), "C_" + theRegionID+"_control");
                theExpressionConsumer.accept(theContext);

                thePW.println("   }");
            }

            for (final Value theValue : theAllValues) {

                final String theNodeID = theRegister.idFor(theValue);

                thePW.print("   ");

                if (theValue instanceof Expression) {
                    thePW.print("E_");
                }
                if (theValue instanceof PrimitiveValue) {
                    thePW.print("P_");
                }
                if (theValue instanceof Variable) {
                    thePW.print("V_");
                }

                thePW.print(theNodeID);

                thePW.print("[");

                if (theValue instanceof Expression) {
                    final Expression theValueExpression = (Expression) theValue;
                    thePW.print("label=\"");
                    thePW.print(theValueExpression.getClass().getSimpleName().replace("Expression", ""));
                    thePW.print("\"");
                }
                if (theValue instanceof PrimitiveValue) {
                    final PrimitiveValue thePrimitive = (PrimitiveValue) theValue;
                    thePW.print("label=\"");
                    thePW.print(thePrimitive.getClass().getSimpleName().replace("Value", ""));
                    thePW.print("\",color=orange");
                }
                if (theValue instanceof Variable) {
                    final Variable theVariable = (Variable) theValue;
                    thePW.print("label=\"");
                    thePW.print(theVariable.getName());
                    thePW.print("\",color=green");
                }

                thePW.println("];");

            }

            for (final DotJump theJump : theJumps) {

                thePW.print("   ");
                thePW.print(theJump.source);
                thePW.print(" -> ");
                thePW.print(theJump.target);
                if (theJump.backEdge) {
                    thePW.println(" [label=\"back-edge\",color=blue,style=dotted];");
                } else {
                    thePW.println("[color=blue,style=dotted];");
                }

            }

            thePW.println("}");
        }
        return theStr.toString();
    }

    private final Map<RegionNode, Set<RegionNode>> dominationCache = new HashMap<>();

    protected Set<RegionNode> dominatedNodesOf(final RegionNode aNode) {
        Set<RegionNode> theResult = dominationCache.get(aNode);
        if (theResult != null) {
            return theResult;
        }
        theResult = new HashSet<>();
        theResult.add(aNode);
        for (final RegionNode theNode : knownNodes) {
            if (theNode.isOnlyReachableThru(aNode)) {
                theResult.add(theNode);
            }
        }
        final Set<RegionNode> theUnmodifiable = Collections.unmodifiableSet(theResult);
        dominationCache.put(aNode, theUnmodifiable);
        return theUnmodifiable;
    }
}