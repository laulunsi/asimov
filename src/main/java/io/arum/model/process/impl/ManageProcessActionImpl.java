package io.arum.model.process.impl;

import io.arum.model.resource.ARUMResourceType;
import io.arum.model.resource.assemblyline.AssemblyLineType;
import io.arum.model.resource.supply.Material;
import io.arum.model.resource.supply.SupplyType;
import io.asimov.agent.process.ManageProcessActionService;
import io.asimov.agent.process.ProcessCompletion;
import io.asimov.agent.process.ProcessManagementWorld;
import io.asimov.microservice.negotiation.ResourceAllocationNegotiator;
import io.asimov.model.ActivityParticipation;
import io.asimov.model.ActivityParticipationResourceInformation;
import io.asimov.model.ResourceAllocation;
import io.asimov.model.process.Activity;
import io.asimov.model.process.Next;
import io.asimov.model.process.Process;
import io.asimov.model.process.Task;
import io.asimov.model.sl.LegacySLUtil;
import io.asimov.model.xml.XmlUtil;
import io.asimov.xml.TProcessType;
import io.asimov.xml.TSkeletonActivityType;
import io.asimov.xml.TSkeletonActivityType.RoleInvolved;
import io.asimov.xml.TSkeletonActivityType.UsedComponent;
import io.coala.agent.AgentID;
import io.coala.bind.Binder;
import io.coala.capability.know.ReasoningCapability;
import io.coala.capability.replicate.ReplicatingCapability;
import io.coala.enterprise.fact.FactID;
import io.coala.enterprise.role.AbstractExecutor;
import io.coala.invoke.ProcedureCall;
import io.coala.invoke.Schedulable;
import io.coala.log.InjectLogger;
import io.coala.model.ModelComponentIDFactory;
import io.coala.time.SimDuration;
import io.coala.time.SimTime;
import io.coala.time.TimeUnit;
import io.coala.time.Trigger;
import jade.semantics.lang.sl.grammar.Formula;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.inject.Inject;

import org.slf4j.Logger;

import rx.Observer;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * {@link ManageProcessActionImpl}
 * 
 * @version $Revision: 1074 $
 * @author <a href="mailto:Rick@almende.org">Rick</a>
 *
 */
public class ManageProcessActionImpl extends
		AbstractExecutor<ProcessCompletion> implements
		ManageProcessActionService
{

	/** */
	private static final long serialVersionUID = 1L;

	/** */
	private static final String sSTART_ACTIVITY_TIME_TOKEN = "startActivityTimeToken";

	/** */
	private static final String sREACHED_EOP = "reachedEndOfProcess";

	private Map<ProcessCompletion.Request, Map<String, Object>> _dss = new HashMap<ProcessCompletion.Request, Map<String, Object>>();

	private Map<String, Object> getDataStore(ProcessCompletion.Request cause)
	{
		if (_dss.containsKey(cause))
			return _dss.get(cause);
		final Map<String, Object> ds = new HashMap<String, Object>();
		_dss.put(cause, ds);
		return ds;
	}

	// @JsonIgnore
	// private final SimulatorService sim;
	//
	// @JsonIgnore
	// private final ReasonerService reasoner;

	@InjectLogger
	@JsonIgnore
	private Logger LOG;

	// @JsonIgnore
	// private final ProcessGeneratorService processGeneratorService;

	/** */
	private Process process;

	/** */
	private TProcessType processXML;

	/** */
	private List<FactID> participatingFactIds = new ArrayList<FactID>();

	/** ONLY FOR DEBUGGING PURPOSES, TO BE REMOVED */
	// @Deprecated
	// private List<ActivityParticipation> participatingFacts = new
	// ArrayList<ActivityParticipation>();

	/** */
	private Map<String, String> resourceSubTypeToAgentIdMap = new HashMap<String, String>();

	/** */
	private ProcedureCall<?> nextActivityjob = null;

	@Inject
	protected ManageProcessActionImpl(final Binder binder)
	{
		super(binder);
		// sim = getBinder().bind(SimulatorService.class);
		// reasoner = getBinder().bind(ReasonerService.class);
		// processGeneratorService = getBinder().bind(
		// ProcessGeneratorService.class);
	}

	/*
		
		@Override
		public void perform(OntoActionBehaviour behaviour) {
			DataStore ds = behaviour.getDataStore();
			switch (behaviour.getState()) {
				case OntoActionBehaviour.START :
				case OntoActionBehaviour.RUNNING :
					if (ds.get("DONE_EXECUTING_MAIN_BEHAVIOUR") == null) {
						QueryResult holdUpQueryResult = getSemanticCapabilities().getMyKBase().query(
								ProcessAgent.getStaticCurrentProcessStateFormula(
										Next.PATTERN
								).instantiate(Next.LATTER_ACTIVITY_TIME_TOKEN, (Term)ds.get(ProcessAgent.sCURRENT_ACTIVITY_TIME_TOKEN))
							);
						if (holdUpQueryResult != null && !holdUpQueryResult.isEmpty()) {
							behaviour.setState(OntoActionBehaviour.RUNNING);
							break;
						}
						ProcessAgent.LOG.trace("Now the process agent will distribute the activities and the nexts from "+(Term)ds.get(ProcessAgent.sCURRENT_ACTIVITY_TIME_TOKEN));
						// do the management
						// ...
						// Create and Distribute Activities and Next's
						// Get Activities
						QueryResult qr = getSemanticCapabilities().getMyKBase().query(
								ProcessAgent.getStaticBelongsToProcessFormula(
										Activity.PATTERN
										.instantiate(Activity.ACTIVITY_TIME_TOKEN, (Term)ds.get(ProcessAgent.sCURRENT_ACTIVITY_TIME_TOKEN))
								)
							);
						TermSequenceNode requiredResources = new TermSequenceNode();
						if (qr != null && !qr.isEmpty()){
							ArrayList activityTerms = new ArrayList();
							Iterator it = qr.getResults().iterator();
							while (it.hasNext()) {
								MatchResult activityMatchResult =  (MatchResult) it.next();
								if (!activityMatchResult.term(Activity.ACTIVITY_NAME).toString().replace("\"", "").equals(Task.START_OF_PROCESS.getName()+"_"+this.processAgent.cProcess.getName()))
									requiredResources = (TermSequenceNode)activityMatchResult.term(Task.TASK_RESOURCE_RESERVATION_SET);
							}
							ProcessAgent.LOG.trace("Distributing next's for resources:"+requiredResources);
						} else {
							ProcessAgent.LOG.trace("NO RESULT FOR:"+ProcessAgent.getStaticBelongsToProcessFormula(
									Activity.PATTERN
									)
								);
						}
						
						// Get Next's
						QueryResult allocatedResourcesQueryResult =
								getSemanticCapabilities().getMyKBase().query(
								new AndNode(
								ProcessAgent.getStaticBelongsToProcessFormula(
										ResourceAllocation.PATTERN
								),
								SL.formula("(member ??"+ResourceAllocation.RESOURCE_REQUIREMENT_ID+" "+requiredResources+")")
								)
								
							);
						if (allocatedResourcesQueryResult == null 
								|| allocatedResourcesQueryResult.isEmpty()){
							behaviour.setState(OntoActionBehaviour.RUNNING);
							ds.put("DONE_EXECUTING_MAIN_BEHAVIOUR",true);
							break;
						}
						Iterator allocatedResourcesIterator = 
							allocatedResourcesQueryResult.getResults().iterator();
						Term nextActivityTimeToken = null;
						while (allocatedResourcesIterator.hasNext()) {
							MatchResult allocatedResourcesMatchResult = (MatchResult)
									allocatedResourcesIterator.next();
							Term actor = allocatedResourcesMatchResult.term(ResourceAllocation.ALLOCATED_AGENT_AID);
							
							QueryResult qrn = null;
							if (nextActivityTimeToken == null) {
								qrn = getSemanticCapabilities().getMyKBase().query(
									ProcessAgent.getStaticBelongsToProcessFormula(
											Next.PATTERN
										
										.instantiate(Next.ACTOR_AGENT_AID, actor)
										.instantiate(Next.FORMER_ACTIVITY_TIME_TOKEN, (Term)ds.get(ProcessAgent.sCURRENT_ACTIVITY_TIME_TOKEN))
									)
								);
								ProcessAgent.LOG.info("query:"+ProcessAgent.getStaticBelongsToProcessFormula(
										Next.PATTERN
										
									.instantiate(Next.ACTOR_AGENT_AID, actor)
									.instantiate(Next.FORMER_ACTIVITY_TIME_TOKEN, (Term)ds.get(ProcessAgent.sCURRENT_ACTIVITY_TIME_TOKEN))
								));
							}else
								qrn = getSemanticCapabilities().getMyKBase().query(
										ProcessAgent.getStaticBelongsToProcessFormula(
												Next.OPTION_PATTERN
											
											.instantiate(Next.ACTOR_AGENT_AID, actor)
											.instantiate(Next.FORMER_ACTIVITY_TIME_TOKEN, (Term)ds.get(ProcessAgent.sCURRENT_ACTIVITY_TIME_TOKEN))
											.instantiate(Next.LATTER_ACTIVITY_TIME_TOKEN, nextActivityTimeToken)
										)
									);
							if (qrn != null && !qrn.isEmpty()){
								ArrayList nextTerms = new ArrayList();
								Iterator it = qrn.getResults().iterator();
								while (it.hasNext()) {
									MatchResult nextMatchResult =  (MatchResult) it.next();
									if (nextActivityTimeToken == null)
										nextActivityTimeToken = nextMatchResult.term(Next.LATTER_ACTIVITY_TIME_TOKEN);
									Term nextTerm = ((Term) 
											SL.instantiate(Next.PATTERN
													.instantiate(Next.FORMER_ACTIVITY_TIME_TOKEN, (Term)ds.get(ProcessAgent.sCURRENT_ACTIVITY_TIME_TOKEN))
													.instantiate(Next.LATTER_ACTIVITY_TIME_TOKEN, nextActivityTimeToken)
													.instantiate(Next.ACTOR_AGENT_AID, actor), nextMatchResult))
											.instantiate(Activity.PROCESS_AGENT_AID, Tools.AID2Term(this.processAgent.getAID())
											);
									nextTerms.add(nextTerm);
									//LOG.trace("Sending to "+actor.toString()+": "+nextTerm.toString());
									Formula processStateFormula = ProcessAgent.getStaticCurrentProcessStateFormula(nextTerm)
											.instantiate(ProcessAgent.PROCESS_NAME, nextMatchResult.term(ProcessAgent.PROCESS_NAME));
									getSemanticCapabilities()
										.getMyKBase().assertFormula(processStateFormula);
									
									ArrayList resources = (ArrayList)allocatedResourcesQueryResult.getResults().clone();
									Iterator resourceIterator = resources.iterator();
									while (resourceIterator.hasNext()) {
										MatchResult resourceMatchResult = (MatchResult)resourceIterator.next();
										// FIXME will not work when resource allocation changes
										if (resourceMatchResult.term(ResourceAllocation.ALLOCATED_AGENT_AID) != actor) {
											Formula resourceRequirementFormula =
													ProcessAgent.getStaticBelongsToProcessFormula(ResourceRequirement.TASK_RESOURCE_PATTERN)
													.instantiate(ResourceRequirement.TASK_RESOURCE, Resource.RESOURCE_PATTERN
															.instantiate(Resource.RESOURCE_NAME, resourceMatchResult.term(ResourceAllocation.RESOURCE_REQUIREMENT_ID))
													);
											QueryResult resourceRequirementQuery = 
													getSemanticCapabilities().getMyKBase().query(resourceRequirementFormula);
											jade.util.leap.Iterator resourceResultIterator = resourceRequirementQuery.getResults().iterator();
											while (resourceResultIterator.hasNext())
												getSemanticCapabilities()
												.inform((Formula)SL.instantiate(resourceRequirementFormula,(MatchResult)resourceResultIterator.next()),actor);
											getSemanticCapabilities()
											.inform((Formula)SL.instantiate(ProcessAgent.getStaticBelongsToProcessFormula(ResourceAllocation.PATTERN),resourceMatchResult), actor);
										}
									}
									getSemanticCapabilities()
										.inform(processStateFormula, actor);
								}
								ds.put(Next.TERM_NAME, nextTerms);
								behaviour.setState(OntoActionBehaviour.RUNNING);
							} else {
								ProcessAgent.LOG.trace("NO RESULT FOR:"+ProcessAgent.getStaticBelongsToProcessFormula(
										Next.PATTERN
										)
									);
								behaviour.setState(OntoActionBehaviour.EXECUTION_FAILURE);
								this.processAgent.logSevere("Process failed!");
								break;
							}
						}
						// ....
						// Notify Process start
						// ....
						// Wait for end of process notification
						// ....
						// now we should be done functionally
	//						if (
	//						getSemanticCapabilities().getMyKBase().query(
	//							Process.getStaticBelongsToProcessFormula(
	//									Activity.PATTERN
	//							)
	//							.instantiate(Activity.ACTIVITY_NAME, SL.string(Process.END_OF_PROCESS))
	//							.instantiate(Activity.ACTIVITY_TIME_TOKEN, nextActivityTimeToken)
	//						) == null) {
	//							ds.put("DONE_EXECUTING_MAIN_BEHAVIOUR", true);
	//						} else {
							ds.remove(ds.get(ProcessAgent.sCURRENT_ACTIVITY_TIME_TOKEN));
							ds.put(ProcessAgent.sCURRENT_ACTIVITY_TIME_TOKEN, nextActivityTimeToken);
						break;
						//}
					}
					if (!this.processAgent.checkIfActionEndTimeIsFeasible(this)) {
						behaviour.setState(OntoActionBehaviour.RUNNING);
						break;
				    }
					ProcessAgent.LOG.trace("Process was succes!");
					if (this.processAgent.isCyclic) {
						java.util.ArrayList<String> args = new java.util.ArrayList<String>();
						for (Object arg : this.processAgent.getArguments())
							args.add(arg.toString());
						String[] strArgs = new String[args.size()];
						args.toArray(strArgs);
						try
						{
							ActionExpression createProcessAgentActionExpression = this.processAgent.createAgentIfNotExistsWithClassTypeAndAttributesAndReturnAID(ProcessAgent.PROCESS_AGENT_CLASS, ProcessAgent.PROCESS_AGENT_TYPE, strArgs, this.processAgent.ASAP+this.processAgent.REPEAT_INTERVAL, this.processAgent.ASAP+this.processAgent.REPEAT_INTERVAL);
							createProcessAgentActionExpression = (ActionExpression) createProcessAgentActionExpression.instantiate("PROPOSED_AGENT_NAME", SL.string(ProcessAgent.PROCESS_AGENT_TYPE+"_"+(new UUID().toString())));
							getSemanticCapabilities().interpret(ProcessAgent.createIntendActionDone(createProcessAgentActionExpression));
							ProcessAgent.LOG.trace("Replicating process with name: "+this.processAgent.getAID().getName());
							ds.put("replicatedProcess", true);
						} catch (ControllerException e)
						{
							this.processAgent.logSevere("Failed to create new procces agent instance for cyclic process.",e);
							e.printStackTrace();
						}
					} 
					behaviour.setState(OntoActionBehaviour.SUCCESS);
					if (this.processAgent.shutDownAfterProcess) {
						ChronosService.getInstance(this.processAgent.replicationID).removeFromFederation(this.processAgent.getAID());
					}
					ProcessGeneratorService.getInstance(this.processAgent.getContainer().getContainerName(), this.processAgent.replicationID).notifyProcessComplete(this.processAgent.getLocalName());
					break;
					
			}
			
		}
		
		*/

	// @Override
	protected ProcessManagementWorld getWorld()
	{
		return getBinder().inject(ProcessManagementWorld.class);
	}

	@SuppressWarnings("deprecation")
	private String getAgentIDByResourceSubType(final String resourceSubType)
	{

		if (resourceSubTypeToAgentIdMap.isEmpty())
		{
			final Formula query = LegacySLUtil
					.getStaticBelongsToProcessFormula(ResourceAllocation.PATTERN);
			// LOG.warn("Current KBase: "
			// + ((FilterKBase) getBinder().inject(ReasonerService.class)
			// .getKBase()).toStrings().toString()
			// .replace(",", ",\n\t"));
			// LOG.warn("Querying: " + query);
			getReasoner().queryToKBase(getReasoner().toQuery(query)).subscribe(
					new Observer<Map<String, Object>>()
					{

						@Override
						public void onError(final Throwable e)
						{
							LOG.error("Problem querying KB", e);
						}

						@Override
						public void onNext(final Map<String, Object> value)
						{
							LOG.info(value
									.get(ResourceAllocation.RESOURCE_REQUIREMENT_ID)
									.toString().toUpperCase()
									+ "==>"
									+ value.get(
											ResourceAllocation.ALLOCATED_AGENT_AID)
											.toString());
							resourceSubTypeToAgentIdMap
									.put(value
											.get(ResourceAllocation.RESOURCE_REQUIREMENT_ID)
											.toString().toUpperCase(),
											value.get(
													ResourceAllocation.ALLOCATED_AGENT_AID)
													.toString());
						}

						@Override
						public void onCompleted()
						{
							synchronized (resourceSubTypeToAgentIdMap)
							{
								resourceSubTypeToAgentIdMap.notifyAll();
							}
						}
					});
		}
		while (resourceSubTypeToAgentIdMap.isEmpty())
		{
			synchronized (resourceSubTypeToAgentIdMap)
			{
				try
				{
					LOG.info("Waiting for start time...");
					resourceSubTypeToAgentIdMap.wait();
				} catch (final InterruptedException ignore)
				{
				}
			}
		}

		final String key = resourceSubType.toUpperCase();
		if (!resourceSubTypeToAgentIdMap.containsKey(key))
			LOG.warn("Resource type agentID not among current beliefs: " + key);
		return resourceSubTypeToAgentIdMap.get(key);
	}

	@Override
	public void onRequested(final ProcessCompletion request)
	{
		// ignore throw new IllegalStateException("NOT IMPLEMENTED");
	}

	

	@SuppressWarnings("deprecation")
	@Override
	public void manageProcessInstanceForType(
			final ProcessCompletion.Request cause)
	{
		final String processType = cause.getProcessTypeID();

		// this.setRequestBuilder(requestBuilder);
		// this.responseBuilder = responseBuilder;
		LOG.info("Managing process: " + processType);
		if (process == null)
		{
			process = getWorld().getProcess(processType);
			processXML = process.toXML();
		}

		if (!this.getDataStore(cause).containsKey(sSTART_ACTIVITY_TIME_TOKEN))
			getReasoner()
					.queryToKBase(
							getReasoner()
									.toQuery(
											LegacySLUtil
													.getStaticBelongsToProcessFormula(Activity.PATTERN),
											Activity.ACTIVITY_NAME,
											Task.START_OF_PROCESS.getName()
													+ "_" + processType))
					.subscribe(new Observer<Map<String, Object>>()
					{

						@Override
						public void onError(final Throwable e)
						{
							LOG.error("Problem querying KB", e);
						}

						@Override
						public void onNext(final Map<String, Object> value)
						{
							getDataStore(cause).put(sSTART_ACTIVITY_TIME_TOKEN,
									value.get(Activity.ACTIVITY_TIME_TOKEN));
							LOG.info("Located start of process");
						}

						@Override
						public void onCompleted()
						{
							synchronized (getDataStore(cause))
							{
								getDataStore(cause).notifyAll();
							}
						}
					});
		// do the management
		// ...
		// Create and Distribute Activities and Next's
		// Get Activities

		// TODO notify scenario replicator
		// processGeneratorService.notifyProcessStarted(getOwnerID().getValue());

		while (!this.getDataStore(cause)
				.containsKey(sSTART_ACTIVITY_TIME_TOKEN))
		{
			synchronized (getDataStore(cause))
			{
				LOG.info("Waiting for start time...");
				try
				{
					this.getDataStore(cause).wait(1000L);
				} catch (final InterruptedException ignore)
				{
				}
			}
		}

		final ProcedureCall<?> nextActivityJob = ProcedureCall.create(this,
				this, NEXT_ACTIVITY_METHOD_ID, cause, this.getDataStore(cause)
						.get(sSTART_ACTIVITY_TIME_TOKEN).toString());
		final SimTime now = getTime();
		getSimulator().schedule(nextActivityJob, Trigger.createAbsolute(now));
	}

	private final static String NEXT_ACTIVITY_METHOD_ID = "processManagementNextActivty";

	private static final String REQUEST_ACTIVITY_PARTICIPATION = "requestActivityParticipation";

	@SuppressWarnings("deprecation")
	@Schedulable(NEXT_ACTIVITY_METHOD_ID)
	public void nextActivity(final ProcessCompletion.Request cause,
			final String currentActivityTimeToken)
	{

		final Map<AgentID, List<String>> nextDistribution = getNextDistributionPerResource(
				cause, currentActivityTimeToken);

		if (getDataStore(cause).get(sREACHED_EOP) != null)
		{
			getBinder().inject(ResourceAllocationNegotiator.class).deAllocate(cause.getSenderID().getValue());
			// TODO notify scenario replicator
			// processGeneratorService.notifyProcessComplete(getOwnerID()
			// .getValue());
			LOG.info("Reached end of process instance " + getID());
			try
			{
				getMessenger().send(
						ProcessCompletion.Result.Builder.forProducer(this,
								cause).withSuccess(true).build());
			} catch (Exception e)
			{
				LOG.error("Failed to send process completion response", e);
			}
			return;
		}

		final List<String> tokenDistribution = new ArrayList<String>();

		for (List<String> dist : nextDistribution.values())
		{
			tokenDistribution.addAll(dist);
		}

		long pick = getRandomizer().getRNG().nextInt(tokenDistribution.size());

		final String nextActivtyTimeToken = tokenDistribution.get((int) pick);

		LOG.info("Found next distribution:" + nextDistribution);

		final CountDownLatch latch = new CountDownLatch(1);
		ReasoningCapability.Query myQuery = getReasoner()
				.toQuery(
						LegacySLUtil
								.getStaticBelongsToProcessFormula(Activity.PATTERN),
						Activity.ACTIVITY_TIME_TOKEN, currentActivityTimeToken);
		getReasoner().queryToKBase(myQuery).take(1)
				.subscribe(new Observer<Map<String, Object>>()
				{

					@Override
					public void onNext(final Map<String, Object> value)
					{
						// LOG.info("Got respone to query: "+value);
						if (value == null)
							latch.countDown();

						nextActivityjob = null;
						SimDuration activityDuration = SimDuration.ZERO;
						String activityID = value.get(Activity.ACTIVITY_NAME)
								.toString();
						if (activityID.contains(Task.START_OF_PROCESS.getName()))
						{
							getSimulator().schedule(
									ProcedureCall.create(
											ManageProcessActionImpl.this,
											ManageProcessActionImpl.this,
											NEXT_ACTIVITY_METHOD_ID, cause,
											nextActivtyTimeToken),
									Trigger.createAbsolute(getTime()));
						} else
						{// if
							// (!activityID.contains(Task.END_OF_PROCESS.getName()))
							// {
							LOG.info("Now the process agent will distribute the activities and the nexts from "
									+ currentActivityTimeToken
									+ " at simTime: " + getTime());

							nextActivityjob = ProcedureCall.create(
									ManageProcessActionImpl.this,
									ManageProcessActionImpl.this,
									NEXT_ACTIVITY_METHOD_ID, cause,
									nextActivtyTimeToken);
							TSkeletonActivityType activityXML = null;
							for (TSkeletonActivityType activity : processXML
									.getActivity())
								if (activity.getId().equals(activityID))
								{
									if (activity.getExecutionTime() != null)
										activityDuration = new SimDuration(
												XmlUtil.gDurationToLong(activity
														.getExecutionTime()),
												TimeUnit.MILLIS);
									activityXML = activity;
								}
							List<ActivityParticipationResourceInformation> resourceParticipationInfo = new ArrayList<ActivityParticipationResourceInformation>();

							participantLoop: for (AgentID agent : nextDistribution
									.keySet())
							{
								ARUMResourceType type = ARUMResourceType.ASSEMBLY_LINE;
								String resourceName = agent.getValue();
								// LOG.warn("CHECK ROLES ----------------:");
								for (RoleInvolved roleInvolved : activityXML
										.getRoleInvolved())
								{
									String requiredAgentID = getAgentIDByResourceSubType(roleInvolved
											.getRoleRef());
									if (requiredAgentID.equals(agent.toString()))
									{
										// LOG.warn("Required: "+requiredAgentID+" == "+agent);
										if (activityXML.getExecutionTime() != null)
											activityDuration = new SimDuration(
													XmlUtil.gDurationToLong(activityXML
															.getExecutionTime()),
													TimeUnit.MILLIS);
										type = ARUMResourceType.PERSON;
										resourceName = roleInvolved
												.getRoleRef();
										LOG.info(agent.getValue() + " is a "
												+ roleInvolved.getRoleRef());
									} else
									{
										// LOG.warn("Required: "+requiredAgentID+" != "+agent);

										LOG.info(agent.getValue()
												+ " is not a "
												+ roleInvolved.getRoleRef());
									}
								}
								// LOG.warn("DONE CHECK ----------------:");
								if (type == ARUMResourceType.ASSEMBLY_LINE)
								{
									for (UsedComponent componentUsed : activityXML
											.getUsedComponent())
									{
										String requiredAgentID = getAgentIDByResourceSubType(componentUsed
												.getComponentRef());
										if (requiredAgentID.equals(agent.toString()))
										{
											// LOG.warn("Required: "+requiredAgentID+" == "+agent);
											for (UsedComponent usedMaterial : activityXML
													.getUsedComponent())
												if (getAgentIDByResourceSubType(
														usedMaterial.getComponentRef()).equalsIgnoreCase(
														agent.toString()))
												{
													activityDuration = new SimDuration(
															XmlUtil.gDurationToLong(usedMaterial
																	.getTimeOfUse()),
															TimeUnit.MILLIS);
													break;
												}
											type = ARUMResourceType.MATERIAL;
											resourceName = componentUsed
													.getComponentRef();
											LOG.info(agent.getValue() + " is a "
													+ componentUsed.getComponentRef());
										} else
										{
											// LOG.warn("Required: "+requiredAgentID+" != "+agent);

											LOG.info(agent.getValue()
													+ " is not a "
													+ componentUsed.getComponentRef());
										}
									}
								if (type == ARUMResourceType.ASSEMBLY_LINE)
								{
									try
									{
										List<AssemblyLineType> assemblyLineTypes = getWorld()
												.getAssemblyLineTypesForAgentID(agent);
										List<String> assemblyLines = new ArrayList<String>();
										for (AssemblyLineType at : assemblyLineTypes)
											assemblyLines.add(at.getName());
										for (String usedAssemblyLineType : activityXML
												.getUsedAssemlyLineType())
											if (!assemblyLines.contains(usedAssemblyLineType))
											{
												LOG.info("Ignoring assemblyLine of types: "
														+ assemblyLines
														+ " because "
														+ usedAssemblyLineType
														+ " was required!");
												continue participantLoop;
											}
									} catch (NullPointerException ne)
									{
										continue participantLoop;
										// No assemblyLine and not the right type of
										// person so continue
									}

								}
								// FIXME Choose a assemblyLine if more are available
								// accoring to a random distribution.
								
								

								}

								// -----------------------------------------------------------------------------
								ActivityParticipationResourceInformation p = new ActivityParticipationResourceInformation()
								.withResourceType(type)
								.withActivityName(activityID)
								.withResourceUsageDuration(
										activityDuration)
								.withResourceAgent(agent)
								.withResourceName(resourceName)
								.withProcessID(
										cause.getProcessTypeID())
								.withInstanceProcessID(
										cause.getID()
												.getValue()
												.toString());
								if (type ==  ARUMResourceType.MATERIAL)
									p.withResourceInstanceName(agent.getValue());
								resourceParticipationInfo
										.add(p);

//								
							}

							synchronized (participatingFactIds)
							{
								List<ActivityParticipation.Request> participatingFacts = new ArrayList<ActivityParticipation.Request>();
								SimTime startOfParticipation = getBinder().inject(ReplicatingCapability.class).getTime();
								for (ActivityParticipationResourceInformation participant : resourceParticipationInfo)
								{
									List<ActivityParticipationResourceInformation> others = new ArrayList<ActivityParticipationResourceInformation>();
									for (ActivityParticipationResourceInformation otherParticipant : resourceParticipationInfo)
									{
										if (!participant
												.equals(otherParticipant))
											others.add(otherParticipant);
									}
									try
									{
//										if (participant.getResourceType() == ARUMResourceType.ASSEMBLY_LINE
//												&& participant
//														.getResourceAgent()
//														.getValue()
//														.startsWith("Person"))
//											throw new IllegalStateException(
//													"Huh WT@%$! Abort");
										ActivityParticipation.Request activityParticipationRequest = ActivityParticipation.Request.Builder
												.forProducer(
														ManageProcessActionImpl.this,
														cause)
												.withReceiverID(
														participant
																.getResourceAgent())
												.withResourceInfo(participant)
												.withOtherResourceInfo(others)
												.build();
										
										participatingFactIds
												.add(activityParticipationRequest
														.getID());
										participatingFacts
												.add(activityParticipationRequest);
									} catch (Exception e)
									{
										LOG.error(
												"Failed to create Activity Participantion Request",
												e);
									}
								}
								ProcedureCall<?> job = ProcedureCall.create(
										ManageProcessActionImpl.this,
										ManageProcessActionImpl.this,
										REQUEST_ACTIVITY_PARTICIPATION,
										participatingFacts);
								getSimulator().schedule(job, Trigger.createAbsolute(startOfParticipation));
							}
						}
						// else {
						// LOG.info("Reached end of process instance "+getID());
						// getBinder().bind(ResourceAllocationNegotiator.class).deAllocate();
						// processGeneratorService.notifyProcessComplete(getOwnerID()
						// .getValue());
						// }
					}

					@Override
					public void onCompleted()
					{
						latch.countDown();
					}

					@Override
					public void onError(final Throwable e)
					{
						e.printStackTrace();
					}
				});

		while (latch.getCount() > 0)
		{
			LOG.info("Waiting for reasonable :-) activity start time...");
			try
			{
				latch.await(1, java.util.concurrent.TimeUnit.SECONDS);
			} catch (final InterruptedException ignore)
			{
			}
		}
	}

	@Schedulable(REQUEST_ACTIVITY_PARTICIPATION)
	public void requestActivityParticipation(
			List<ActivityParticipation.Request> requests)
	{
		for (ActivityParticipation.Request r : requests)
		{
			try
			{
				getMessenger().send(r);
			} catch (Exception e)
			{
				LOG.error("Failed to send Activity Participantion Request", e);
			}
		}
	}

	/**
	 * @return
	 */
	@SuppressWarnings("deprecation")
	private Map<AgentID, List<String>> getNextDistributionPerResource(
			final ProcessCompletion.Request cause,
			final String currentActivityTimeToken)
	{
		final Map<String, AgentID> agentIds = new HashMap<String, AgentID>();
		final Map<AgentID, List<String>> result = new HashMap<AgentID, List<String>>();

		final CountDownLatch latch = new CountDownLatch(1);
		getReasoner()
				.queryToKBase(
						getReasoner()
								.toQuery(
										LegacySLUtil
												.getStaticBelongsToProcessFormula(Next.OPTION_PATTERN),
										Next.FORMER_ACTIVITY_TIME_TOKEN,
										currentActivityTimeToken)).subscribe(
						new Observer<Map<String, Object>>()
						{
							@Override
							public void onNext(final Map<String, Object> value)
							{
								if (value == null)
								{
									getDataStore(cause).put(sREACHED_EOP, true);
									latch.countDown();
									return; // Actor reached end of process!
								}
								String actor = value.get(Next.ACTOR_AGENT_AID)
										.toString();
								AgentID agentId;
								// FIXME is always false ??
								if (agentIds.containsKey(actor))
								{
									agentId = agentIds.get(actor);
								} else
								{
									agentId = getBinder().inject(
											ModelComponentIDFactory.class)
											.createAgentID(actor);
									agentIds.put(actor, agentId);
								}
								List<String> distribution = new ArrayList<String>();
								if (result.containsKey(agentId))
								{
									distribution = result.get(agentId);
								} else
								{
									result.put(agentId, distribution);
								}

								if (value != null)
								{
									long chance = Long.valueOf(value.get(
											Next.CHANCE).toString());
									for (int i = 0; i < chance; i++)
										distribution
												.add(value
														.get(Next.LATTER_ACTIVITY_TIME_TOKEN)
														.toString());
								}
							}

							@Override
							public void onCompleted()
							{
								latch.countDown();
							}

							@Override
							public void onError(final Throwable e)
							{
								e.printStackTrace();
							}
						});

		while (latch.getCount() > 0)
		{
			LOG.info("Waiting for reasonable :-) next activity time...");
			try
			{
				latch.await(1, java.util.concurrent.TimeUnit.SECONDS);
			} catch (final InterruptedException ignore)
			{
			}
		}

		return result;
	}

	// /**
	// * @return the requestBuilder
	// */
	// public ActivityParticipation.Request.Builder getRequestBuilder()
	// {
	// return this.requestBuilder;
	// }
	//
	// /**
	// * @param requestBuilder the requestBuilder to set
	// */
	// public void setRequestBuilder(
	// ActivityParticipation.Request.Builder requestBuilder)
	// {
	// this.requestBuilder = requestBuilder;
	// }

	@Override
	public void notifyActivityParticipationResult(
			ActivityParticipation.Result result)
	{
		synchronized (participatingFactIds)
		{
			if (participatingFactIds.remove(result.getID().getCauseID()))
				LOG.info(result.getResourceInfo().getResourceName()
						+ " participated in activity "
						+ result.getResourceInfo().getActivityName() + " at "
						+ getTime());

			// for (ActivityParticipation fact : participatingFacts) {
			// if (fact.getID().equals(result.getID().getCauseID())) {
			// participatingFacts.remove(fact);
			// break;
			// }
			// }
			if (participatingFactIds.isEmpty() && nextActivityjob != null)
			{
				getSimulator().schedule(nextActivityjob,
						Trigger.createAbsolute(getTime()));
				nextActivityjob = null;
			} else
			{
				LOG.info("Still waiting for participation of factID's: "
						+ participatingFactIds);
				// for (ActivityParticipation fact : participatingFacts) {
				// LOG.info("Not ready yet:"+JsonUtil.toPrettyJSON(fact.getResourceInfo()));
				// }
			}
		}

	}
}