/* $Id: LegacySLUtil.java 1078 2014-09-25 13:25:37Z suki $
 * $URL: https://redmine.almende.com/svn/a4eesim/trunk/adapt4ee-model/src/main/java/eu/a4ee/model/jsa/util/LegacySLUtil.java $
 * 
 * Part of the EU project Adapt4EE, see http://www.adapt4ee.eu/
 * 
 * @license
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Copyright (c) 2010-2013 Almende B.V. 
 */
package io.asimov.model.sl;

import io.asimov.model.Resource;
import io.asimov.model.ResourceAllocation;
import io.asimov.model.ResourceRequirement;
import io.asimov.model.Time;
import io.asimov.model.process.Process;
import io.asimov.model.process.Task;
import io.asimov.model.process.Transition;
import io.asimov.reasoning.sl.ASIMOVSLReasoningCapability;
import io.asimov.reasoning.sl.KBase;
import io.asimov.reasoning.sl.NotNode;
import io.coala.agent.AgentID;
import io.coala.capability.embody.Percept;
import io.coala.config.CoalaPropertyGetter;
import io.coala.jsa.sl.SLParsableSerializable;
import io.coala.log.LogUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import rx.Observer;

/**
 * {@link LegacySLUtil}
 * 
 * @date $Date: 2014-09-25 15:25:37 +0200 (do, 25 sep 2014) $
 * @version $Revision: 1078 $
 * @author <a href="mailto:suki@almende.org">suki</a>
 *
 */
public class LegacySLUtil implements ASIMOVAgents {

	/** */
	private static Logger LOG = LogUtil.getLogger(LegacySLUtil.class);

	final static Set<AgentID> rulesAddedToAgentSet = new HashSet<AgentID>();

	private static final String EXCLUSIVE_RESOURCE_ID_PROPERTY = "exclusiveResources";

	public static void makeObservation(final Observer<Percept> result,
			final Process cProcess, final AgentID agentID) {
		
		String pInstanceName = agentID.getValue();

		// Add tasks to KBase
		for (Task task : cProcess.getTasks())
			result.onNext(new NodePercept(getBelongsToProcessFormula(
					pInstanceName, task.toSL())));

		// Add transitions to KBase
		for (Transition transition : cProcess.getTransitions())
			result.onNext(new NodePercept(getBelongsToProcessFormula(
					pInstanceName, transition.toSL())));

		// Add resources to KBase
		for (ResourceRequirement resource : cProcess.getRequiredResources()
				.values())
			result.onNext(new NodePercept(getBelongsToProcessFormula(
					pInstanceName, resource.toSL())));

		result.onCompleted();
	}

	@SuppressWarnings("unchecked")
	public static Serializable convertAgentID(final AgentID agentID,
			final Serializable forumla) {
		ASIMOVNode<?> f;
		try {
			f = ASIMOVSLReasoningCapability.getSLForObject(ASIMOVNode.class,
					new SLParsableSerializable(forumla.toString()));
		} catch (Exception e) {
			LOG.error("Failed to parse Serializable to formula", e);
			return null;
		}
		ASIMOVTerm resourceMatchPattern = ResourceRequirement.TASK_RESOURCE_PATTERN
				.instantiate(ResourceRequirement.TASK_RESOURCE,
						Resource.RESOURCE_PATTERN
				// .instantiate(Resource.RESOURCE_NAME,agentName)
				// .instantiate(Resource.RESOURCE_SUB_TYPE,
				// SL.string(requirement.getResource().getSubTypeID().getName()))
				// .instantiate(Resource.RESOURCE_TYPE,SL.string(requirement.getResource().getTypeID().getName()))
				)
				.instantiate(ResourceRequirement.TASK_RESOURCE_AMOUNT,
						SL.integer(1))
				.instantiate(ResourceRequirement.TASK_RESOURCE_DURATION,
						new Time().withMillisecond(0).toSL());
		ASIMOVFormula resourceFormula = SLConvertible.ASIMOV_PROPERTY_SET_FORMULA
				.instantiate(SLConvertible.sASIMOV_PROPERTY,
						SL.string(ResourceRequirement.TASK_RESOURCE))
				.instantiate(SLConvertible.sASIMOV_KEY,
						SL.string(ResourceRequirement.TASK_RESOURCE_TERM_NAME))
				.instantiate(SLConvertible.sASIMOV_VALUE, resourceMatchPattern);
		List<ASIMOVFormula> formulas = new ArrayList<ASIMOVFormula>();
		if (f instanceof ASIMOVAndNode) {
			Iterator<?> formulaIterator = formulas.iterator();
			Map<String,Object> matchedResourceResult = null;
			while (matchedResourceResult == null && formulaIterator.hasNext()) {
				ASIMOVFormula matching = (ASIMOVFormula) formulaIterator.next();
				matchedResourceResult = KBase.matchNode(resourceFormula,matching);
			}

			List<ASIMOVFormula> resultList = new ArrayList<ASIMOVFormula>();

			ASIMOVFormula result = BELONGS_TO_PROCESS_FORMULA.instantiate(
					PROCESS_PROPERTY,
					ResourceAllocation.PATTERN.instantiate(
							ResourceAllocation.RESOURCE_REQUIREMENT_ID,
							SL.string(matchedResourceResult.get(Resource.RESOURCE_SUB_TYPE)
									.toString().replace("\"", ""))))
					.instantiate(PROCESS_NAME, SL.string(agentID.getValue()));
			resultList.add(result);

			while (formulaIterator.hasNext()) {
				ASIMOVFormula matching = (ASIMOVFormula) formulaIterator.next();
				Map<String,Object> matchedEquipmentResult = KBase.matchNode(resourceFormula
						,matching);
				if (matchedEquipmentResult != null)
					resultList.add(BELONGS_TO_PROCESS_FORMULA.instantiate(
							PROCESS_PROPERTY,
							ResourceAllocation.PATTERN.instantiate(
									ResourceAllocation.RESOURCE_REQUIREMENT_ID,
									SL.string(matchedEquipmentResult
											.get(Resource.RESOURCE_SUB_TYPE).toString())))
							.instantiate(PROCESS_NAME,
									SL.string(agentID.getValue())));
			}
			if (resultList.size() < 2)
				return new SLParsableSerializable(result.toString());
			ASIMOVAndNode rNode = new ASIMOVAndNode();
			Iterator<?> it = resultList.iterator();
			for (int i = 0; it.hasNext(); i++) {
				ASIMOVFormula ff = (ASIMOVFormula) it.next();
				rNode = (ASIMOVAndNode) rNode.instantiate(""+i, ff);
			}
			return new SLParsableSerializable(rNode.toJSON());
		}
		throw new IllegalStateException("Formula not well formatted.");
	}

	public static ASIMOVFormula getBelongsToProcessFormula(final String processName,
			ASIMOVTerm property) {
		return getStaticBelongsToProcessFormula(property).instantiate(
				PROCESS_NAME, SL.string(processName));
	}

	@Deprecated
	public static ASIMOVFormula getStaticBelongsToProcessFormula(ASIMOVTerm property) {
		return (ASIMOVFormula) SL.instantiate(BELONGS_TO_PROCESS_FORMULA,
				PROCESS_PROPERTY, property);
	}

	public static class NodePercept implements Percept,
			SLConvertible<NodePercept> {

		/** */
		private static final long serialVersionUID = 1L;

		private ASIMOVNode<?> node;

		/** zero argument constructor */
		public NodePercept() {
			super();
		}

		public <N extends ASIMOVNode<N>> NodePercept(final N node) {
			fromSL(node);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <N extends ASIMOVNode<N>> N toSL() {
			return (N) node;
		}

			@Override
		public <N extends ASIMOVNode<N>> NodePercept fromSL(N node) {
			this.node = node;
			return this;
		}

		@Override
		public String toString() {
			return (this.node == null) ? super.toString() : this.node
					.toJSON();
		}

	}
//
//	private static java.util.ArrayList<NodePercept> getRules(
//			final String processTypeID, final AgentID requestor) {
//		final java.util.ArrayList<NodePercept> rules = new java.util.ArrayList<NodePercept>();
//		if (rulesAddedToAgentSet.contains(requestor))
//			return rules;
//		rulesAddedToAgentSet.add(requestor);
//		VariableNode a = new VariableNode("?aid");
//		VariableNode formerActivityName = new VariableNode("?former_activity");
//		VariableNode latterActivityName = new VariableNode("?latter_activity");
//		VariableNode r = new VariableNode("?resource_reservation");
//		VariableNode rs = new VariableNode("resource_reservations_set");
//		VariableNode p = new VariableNode("?process_id");
//		VariableNode tl = new VariableNode("?latter_time_id");
//		VariableNode tf = new VariableNode("?former_time_id");
//		VariableNode tc = new VariableNode("transition_cases");
//		VariableNode ntc = new VariableNode("?transition_cases");
//		VariableNode tcar = new VariableNode("?the_cardinality");
//		VariableNode it = new VariableNode("?input_tasks_set");
//
//		final String tpfx = "t";
//		ASIMOVFormula transition_t = FOLToSLHelper
//				.pfx(getStaticBelongsToProcessFormula(Transition.PATTERN), tpfx)
//				.instantiate(FOLToSLHelper.pfx(Transition.TRANSITION_ID, tpfx),
//						tl)
//				.instantiate(FOLToSLHelper.pfx(PROCESS_NAME, tpfx), p);
//
//		ASIMOVFormula activity = getStaticBelongsToProcessFormula(Activity.PATTERN)
//				.instantiate(Activity.ACTIVITY_TIME_TOKEN, tf)
//				.instantiate(PROCESS_NAME, p)
//				.instantiate(Activity.ACTIVITY_NAME, formerActivityName);
//
//		ASIMOVFormula resourceAllocation = getStaticBelongsToProcessFormula(
//				ResourceAllocation.PATTERN).instantiate(PROCESS_NAME, p);
//
//		ASIMOVFormula resourceReservation = getStaticBelongsToProcessFormula(
//				ResourceRequirement.TASK_RESOURCE_PATTERN).instantiate(
//				ResourceRequirement.TASK_RESOURCE, Resource.RESOURCE_PATTERN)
//				.instantiate(PROCESS_NAME, p);
//
//		ASIMOVFormula nextOption = getStaticBelongsToProcessFormula(
//				Next.OPTION_PATTERN).instantiate(PROCESS_NAME, p);
//
//		ASIMOVFormula anyResource = new AndNode(
//				resourceAllocation.instantiate(
//						ResourceAllocation.ALLOCATED_AGENT_AID, a).instantiate(
//						ResourceAllocation.RESOURCE_REQUIREMENT_ID, r),
//				new AndNode(
//						resourceReservation.instantiate(Resource.RESOURCE_NAME,
//								r),
//						SL.formula("(member ??member ??set)")
//								.instantiate("member", r)
//								.instantiate(
//										"set",
//										new AnyNode(
//												rs,
//												activity.instantiate(
//														Task.TASK_RESOURCE_RESERVATION_SET,
//														rs)))));
//
//		ASIMOVFormula transition_t_cardinality = new AndNode(new AndNode(transition_t
//				.instantiate(FOLToSLHelper.pfx(Transition.CASE_IDS, tpfx), ntc)
//				.instantiate(
//						FOLToSLHelper.pfx(Transition.INPUT_TASK_NAMES, tpfx),
//						it), SL.formula("(card " + ntc + " " + tcar + ")")),
//				SL.formula("(member "
//						+ ntc
//						+ " "
//						+ new SomeNode(tc, new AndNode(SL.formula("(member "
//								+ formerActivityName + " " + it + ")"),
//								transition_t.instantiate(
//										FOLToSLHelper.pfx(Transition.CASE_IDS,
//												tpfx), tc).instantiate(
//										FOLToSLHelper.pfx(
//												Transition.INPUT_TASK_NAMES,
//												tpfx), it))) + ")"));
//
//		ASIMOVFormula nextForAIDQuery = new AndNode(anyResource, activity);
//
//		ASIMOVFormula nextOptions = FOLToSLHelper.generateImpliesNode(
//				new AndNode(nextForAIDQuery, new AndNode(
//						getStaticBelongsToProcessFormula(Activity.PATTERN)
//								.instantiate(Activity.ACTIVITY_TIME_TOKEN, tl)
//								.instantiate(PROCESS_NAME, p)
//								.instantiate(Activity.ACTIVITY_NAME,
//										latterActivityName),
//						transition_t_cardinality)),
//				nextOption
//						.instantiate(Next.LATTER_ACTIVITY_TIME_TOKEN, tl)
//						.instantiate(Next.FORMER_ACTIVITY_TIME_TOKEN, tf)
//						.instantiate(Next.FORMER_ACTIVITY_NAME,
//								formerActivityName)
//						.instantiate(Next.LATTER_ACTIVITY_NAME,
//								latterActivityName)
//						.instantiate(Next.ACTOR_AGENT_AID, a)
//						.instantiate(Next.CHANCE, tcar));
//
//		rules.add(new NodePercept(nextOptions));
//
//		VariableNode x = new VariableNode("?activity");
//		VariableNode res = new VariableNode("?resources");
//		VariableNode c = new VariableNode("?case_ids");
//		VariableNode p_id = new VariableNode("?process_id");
//		VariableNode t = new VariableNode("?time_id");
//		VariableNode i = new VariableNode("input_task_names");
//		VariableNode is = new VariableNode("?input_task_names_set");
//		VariableNode o = new VariableNode("output_task_names");
//		VariableNode os = new VariableNode("?output_task_names_set");
//
//		ASIMOVFormula transition = getStaticBelongsToProcessFormula(
//				Transition.PATTERN).instantiate(Transition.TRANSITION_ID, t)
//				.instantiate(PROCESS_NAME, p_id);
//
//		ASIMOVFormula leftFormulaA1 = new AndNode(transition, new AndNode(
//				SL.formula("(member " + x + " " + os + ")"),
//				SL.formula("(member "
//						+ os
//						+ " "
//						+ new SomeNode(o, transition.instantiate(
//								Transition.OUTPUT_TASK_NAMES, o)) + ")")));
//
//		ASIMOVFormula leftFormulaA2 = new AndNode(transition.instantiate(
//				Transition.INPUT_TASK_NAMES,
//				new TermSequenceNode(new ListOfTerm(new Term[] { SL
//						.string(Task.START_OF_PROCESS.getName() + "_"
//								+ processTypeID) }))), new AndNode(
//				SL.formula("(member " + x + " " + is + ")"),
//				SL.formula("(member "
//						+ is
//						+ " "
//						+ new SomeNode(i, transition.instantiate(
//								Transition.INPUT_TASK_NAMES, i)) + ")")));
//
//		ASIMOVFormula leftFormulaB = getStaticBelongsToProcessFormula(Task.PATTERN)
//				.instantiate(Task.TASK_NAME, x)
//				.instantiate(Task.TASK_RESOURCE_RESERVATION_SET, res)
//				.instantiate(Task.CASE_IDS, c).instantiate(PROCESS_NAME, p_id);
//
//		ASIMOVFormula rightFormula2 = getStaticBelongsToProcessFormula(
//				Activity.PATTERN).instantiate(Activity.ACTIVITY_NAME, x)
//				.instantiate(Activity.ACTIVITY_TIME_TOKEN, t)
//				.instantiate(Task.TASK_NAME, x)
//				.instantiate(Task.TASK_RESOURCE_RESERVATION_SET, res)
//				.instantiate(Task.CASE_IDS, c).instantiate(PROCESS_NAME, p_id);
//
//		rules.add(new NodePercept(FOLToSLHelper.generateImpliesNode(
//				new AndNode(leftFormulaB, leftFormulaA1), rightFormula2)));
//
//		rules.add(new NodePercept(FOLToSLHelper.generateImpliesNode(
//				new AndNode(leftFormulaB, leftFormulaA2), rightFormula2)));
//
//		return rules;
//	}

	public static ASIMOVNode<?> countAllocationScore(final String keyName) {
		return new ASIMOVFunctionNode(getStaticBelongsToProcessFormula(
						ResourceAllocation.PATTERN))
					.withFunctionType(ASIMOVFunctionNode.CARDINALITY)
					.withResultKey(keyName);

	}

	public static ASIMOVNode<?> requestResourceAllocationForRequirement(
			ResourceRequirement requirement) {
		ASIMOVNode<?> result = null;
		//VariableNode procesName = new VariableNode("procesName");
		//VariableNode processCard = new VariableNode("?processCard");
		// (and (member ??processCard (set 0)) (card (some ?procesName
		// (BELONGS_TO_PROCESS (RESOURCE_ALLOCATION :ALLOCATED_AGENT_AID
		// ??ALLOCATED_AGENT_AID :RESOURCE_REQUIREMENT_ID
		// ??RESOURCE_REQUIREMENT_ID) ?procesName)) ??processCard))
//		ASIMOVFormula notAllocated = new AndNode(SL.formula("(member " + processCard
//				+ " (set 0))"), SL.formula("(card "
//				+ new SomeNode(procesName, getStaticBelongsToProcessFormula(
//						ResourceAllocation.PATTERN).instantiate(PROCESS_NAME,
//						procesName)) + " " + processCard + ")"));
		ASIMOVFormula notAllocated = new NotNode(getStaticBelongsToProcessFormula(
						ResourceAllocation.PATTERN));
						//.instantiate(PROCESS_NAME,
						//procesName));
		ASIMOVTerm resourceMatchPattern = ResourceRequirement.TASK_RESOURCE_PATTERN
				.instantiate(
						ResourceRequirement.TASK_RESOURCE,
						Resource.RESOURCE_PATTERN
								// .instantiate(Resource.RESOURCE_NAME,agentName)
								.instantiate(
										Resource.RESOURCE_SUB_TYPE,
										SL.string(requirement.getResource()
												.getSubTypeID().getName()))
								.instantiate(
										Resource.RESOURCE_TYPE,
										SL.string(requirement.getResource()
												.getTypeID().getName())))
				.instantiate(ResourceRequirement.TASK_RESOURCE_AMOUNT,
						SL.integer(1))
				.instantiate(ResourceRequirement.TASK_RESOURCE_DURATION,
						new Time().withMillisecond(0).toSL());
		ASIMOVFormula resourceFormula = SLConvertible.ASIMOV_PROPERTY_SET_FORMULA
				.instantiate(SLConvertible.sASIMOV_PROPERTY,
						SL.string(ResourceRequirement.TASK_RESOURCE))
				.instantiate(SLConvertible.sASIMOV_KEY,
						SL.string(ResourceRequirement.TASK_RESOURCE_TERM_NAME))
				.instantiate(SLConvertible.sASIMOV_VALUE, resourceMatchPattern);

		// if (!requirement.getResource().getTypeID().getName()
		// .equalsIgnoreCase(ARUMResourceType.PERSON.name()) ||
		// !isExclusive(requirement))
		// result = resourceFormula;
		// else
		result = new ASIMOVAndNode(notAllocated, resourceFormula);

		// LOG.error(result);
		return result;
	}

	public static ASIMOVNode<?> requestResourceAllocationForRequirement(
			final String type, final String subType) {
		ASIMOVNode<?> result = null;
//		VariableNode procesName = new VariableNode("procesName");
//		VariableNode processCard = new VariableNode("?processCard");
//		// (and (member ??processCard (set 0)) (card (some ?procesName
//		// (BELONGS_TO_PROCESS (RESOURCE_ALLOCATION :ALLOCATED_AGENT_AID
//		// ??ALLOCATED_AGENT_AID :RESOURCE_REQUIREMENT_ID
//		// ??RESOURCE_REQUIREMENT_ID) ?procesName)) ??processCard))
//		ASIMOVFormula notAllocated = new AndNode(SL.formula("(member " + processCard
//				+ " (set 0))"), SL.formula("(card "
//				+ new SomeNode(procesName, getStaticBelongsToProcessFormula(
//						ResourceAllocation.PATTERN).instantiate(PROCESS_NAME,
//						procesName)) + " " + processCard + ")"));
		ASIMOVFormula notAllocated = new NotNode(getStaticBelongsToProcessFormula(
				ResourceAllocation.PATTERN));
		ASIMOVTerm resourceMatchPattern = ResourceRequirement.TASK_RESOURCE_PATTERN
				.instantiate(
						ResourceRequirement.TASK_RESOURCE,
						Resource.RESOURCE_PATTERN
								// .instantiate(Resource.RESOURCE_NAME,agentName)
								.instantiate(
										Resource.RESOURCE_SUB_TYPE,
										SL.string(subType))
								.instantiate(
										Resource.RESOURCE_TYPE,
										SL.string(type)))
				.instantiate(ResourceRequirement.TASK_RESOURCE_AMOUNT,
						SL.integer(1))
				.instantiate(ResourceRequirement.TASK_RESOURCE_DURATION,
						new Time().withMillisecond(0).toSL());
		ASIMOVFormula resourceFormula = SLConvertible.ASIMOV_PROPERTY_SET_FORMULA
				.instantiate(SLConvertible.sASIMOV_PROPERTY,
						SL.string(ResourceRequirement.TASK_RESOURCE))
				.instantiate(SLConvertible.sASIMOV_KEY,
						SL.string(ResourceRequirement.TASK_RESOURCE_TERM_NAME))
				.instantiate(SLConvertible.sASIMOV_VALUE, resourceMatchPattern);

		// if (!requirement.getResource().getTypeID().getName()
		// .equalsIgnoreCase(ARUMResourceType.PERSON.name()) ||
		// !isExclusive(requirement))
		// result = resourceFormula;
		// else
		result = new ASIMOVAndNode(notAllocated, resourceFormula);

		// LOG.error(result);
		return result;
	}

	/**
	 * Check if a resource of a certain type or sub-type is exclusively
	 * allocated or not
	 * 
	 * @param requirement
	 *            the {@see ResourceRequirement}
	 * @return false if instance can be claimed by multiple allocators,
	 *         otherwise true.
	 */
	private static boolean isExclusive(ResourceRequirement requirement) {
		CoalaPropertyGetter getter = new CoalaPropertyGetter(
				EXCLUSIVE_RESOURCE_ID_PROPERTY);
		if (getter != null) {
			String[] result = getter.getJSON(String[].class);
			if (result != null) {
				for (int i = 0; i < result.length; i++) {
					if (result[i].equals("*")
							|| requirement.getResource().getTypeID().getName()
									.equalsIgnoreCase(result[i])
							|| requirement.getResource().getSubTypeID()
									.getName().equalsIgnoreCase(result[i]))
						return true;
				}
			}
		}
		return false;
	}

}
