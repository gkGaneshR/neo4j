/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v3_4.planner.logical

import org.neo4j.cypher.internal.util.v3_4.InternalException
import org.neo4j.cypher.internal.v3_4.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.frontend.v3_4.ast.rewriters.PatternExpressionPatternElementNamer
import org.neo4j.cypher.internal.ir.v3_4.{IdName, QueryGraph}
import org.neo4j.cypher.internal.v3_4.expressions._

trait QueryGraphSolver {
  def plan(queryGraph: QueryGraph, context: LogicalPlanningContext): LogicalPlan
  def planPatternExpression(planArguments: Set[IdName], expr: PatternExpression,context: LogicalPlanningContext): (LogicalPlan, PatternExpression)
  def planPatternComprehension(planArguments: Set[IdName], expr: PatternComprehension, context: LogicalPlanningContext): (LogicalPlan, PatternComprehension)
}

trait PatternExpressionSolving {

  self: QueryGraphSolver =>

  import org.neo4j.cypher.internal.ir.v3_4.helpers.ExpressionConverters._

  def planPatternExpression(planArguments: Set[IdName], expr: PatternExpression, context: LogicalPlanningContext): (LogicalPlan, PatternExpression) = {
    val dependencies = expr.dependencies.map(IdName.fromVariable)
    val qgArguments = planArguments intersect dependencies
    val (namedExpr, namedMap) = PatternExpressionPatternElementNamer(expr)
    val qg = namedExpr.asQueryGraph.withArgumentIds(qgArguments)
    val plan = planQueryGraph(qg, namedMap, context)
    (plan, namedExpr)
  }

  def planPatternComprehension(planArguments: Set[IdName], expr: PatternComprehension, context: LogicalPlanningContext): (LogicalPlan, PatternComprehension) = {
    val asQueryGraph = expr.asQueryGraph
    val qgArguments = planArguments intersect asQueryGraph.idsWithoutOptionalMatchesOrUpdates
    val qg = asQueryGraph.withArgumentIds(qgArguments).addPredicates(expr.predicate.toIndexedSeq:_*)
    val plan: LogicalPlan = planQueryGraph(qg, Map.empty, context)
    (plan, expr)
  }

  private def planQueryGraph(qg: QueryGraph, namedMap: Map[PatternElement, Variable], context: LogicalPlanningContext): LogicalPlan = {
    val namedNodes = namedMap.collect { case (_: NodePattern, identifier) => identifier }
    val namedRels = namedMap.collect { case (_: RelationshipChain, identifier) => identifier }
    val patternPlanningContext = context.forExpressionPlanning(namedNodes, namedRels)
    self.plan(qg, patternPlanningContext)
  }
}

trait TentativeQueryGraphSolver extends QueryGraphSolver with PatternExpressionSolving {
  def tryPlan(queryGraph: QueryGraph, context: LogicalPlanningContext): Option[LogicalPlan]
  def plan(queryGraph: QueryGraph, context: LogicalPlanningContext): LogicalPlan =
    tryPlan(queryGraph, context).getOrElse(throw new InternalException("Failed to create a plan for the given QueryGraph " + queryGraph))
}
