package io.arum.model.scenario;

import io.arum.model.AbstractARUMOrganizationtWorld;
import io.arum.model.resource.assemblyline.AssemblyLine;
import io.arum.model.resource.assemblyline.AssemblyLineResourceManagementOrganization;
import io.arum.model.resource.person.Person;
import io.arum.model.resource.person.PersonResourceManagementOrganization;
import io.arum.model.resource.supply.Material;
import io.arum.model.resource.supply.MaterialResourceManagementOrganization;
import io.arum.run.RunUseCase;
import io.asimov.agent.scenario.Replication;
import io.asimov.agent.scenario.ScenarioManagementWorld;
import io.asimov.agent.scenario.SimStatus;
import io.asimov.db.Datasource;
import io.asimov.messaging.ASIMOVMessage;
import io.asimov.util.extrapolator.EventExtrapolator;
import io.asimov.xml.SimulationFile;
import io.asimov.xml.TUseCase;
import io.coala.agent.Agent;
import io.coala.agent.AgentID;
import io.coala.bind.Binder;
import io.coala.capability.configure.ConfiguringCapability;
import io.coala.capability.replicate.ReplicatingCapability;
import io.coala.capability.replicate.ReplicationConfig;
import io.coala.exception.CoalaException;
import io.coala.invoke.ProcedureCall;
import io.coala.invoke.Schedulable;
import io.coala.log.InjectLogger;
import io.coala.model.ModelComponentIDFactory;
import io.coala.random.RandomDistribution;
import io.coala.resource.FileUtil;
import io.coala.time.SimDuration;
import io.coala.time.SimTime;
import io.coala.time.SimTimeFactory;
import io.coala.time.TimeUnit;
import io.coala.time.Trigger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.measure.unit.SI;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;
import org.jscience.physics.amount.Amount;

import rx.Observable;
import rx.Observer;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

/**
 * {@link ScenarioManagementWorldImpl}
 * 
 * @version $Revision: 1084 $
 * @author <a href="mailto:Rick@almende.org">Rick</a>
 * 
 */
public class ScenarioManagementWorldImpl extends AbstractARUMOrganizationtWorld
		implements ScenarioManagementWorld {

	/** */
	private static final long serialVersionUID = 1L;

	@InjectLogger
	private Logger LOG;

	/** */
	private final Set<AgentID> assemblyLineIDs = Collections
			.synchronizedSet(new HashSet<AgentID>());

	/** */
	private final Set<AgentID> personIDs = Collections
			.synchronizedSet(new HashSet<AgentID>());

	/** */
	private final Set<Person> persons = Collections
			.synchronizedSet(new HashSet<Person>());

	/** */
	private final Set<AssemblyLine> assemblyLines = Collections
			.synchronizedSet(new HashSet<AssemblyLine>());

	/** */
	private final Set<Material> materials = Collections
			.synchronizedSet(new HashSet<Material>());

	/** */
	private final Set<AgentID> materialIDs = Collections
			.synchronizedSet(new HashSet<AgentID>());

	/** */
	private final List<ResourceEvent> agents = new ArrayList<ResourceEvent>();

	/** */
	private Replication replication = null;

	/** */
	private UseCaseScenario scenario = null;
	
	private Subject<Integer,Integer> resourceHash = BehaviorSubject.create(0);

	/**
	 * {@link ScenarioManagementWorldImpl} constructor
	 * 
	 * @param binder
	 */
	@Inject
	protected ScenarioManagementWorldImpl(final Binder binder) {
		super(binder);
	}

	public <T extends Agent> ResourceEvent getResourceEventForNewAgent(
			final AgentID agentID, final Class<T> agentType,
			final SimTime eventTime) {
		return new ResourceEvent() {
			@Override
			public AgentID getResourceID() {
				return agentID;
			}

			@Override
			public Class<T> getResourceType() {
				return agentType;
			}

			@Override
			public ResourceEventType getEventType() {
				return ResourceEventType.ADDED;
			}

			@Override
			public SimTime getEventTime() {
				return eventTime;
			}

			@Override
			public int compareTo(ResourceEvent o) {
				if (getEventTime() == null)
					return -1;
				else if (o.getEventTime() != null)
					return getEventTime().toDate().compareTo(
							o.getEventTime().toDate());
				else
					return 0;
			}
		};
	}

	@Override
	public void initialize() throws Exception {
		final Datasource ds = getBinder().inject(Datasource.class);
		final ReplicationConfig cfg = getBinder().inject(
				ReplicationConfig.class);

		this.replication = ds.findReplication();

		File simFileURI = (RunUseCase.sourceFile != null) ? new File(
				RunUseCase.sourceFile.getAbsolutePath()) : new File(
				getProperty(SCENARIO_FILE_KEY).get());
		SimulationFile simFile = null;
		InputStream is = FileUtil.getFileAsInputStream(simFileURI);

		if (is == null)
			throw new NullPointerException("Simulationfile not found at URI: "
					+ simFileURI);
		try {
			JAXBContext jc = JAXBContext.newInstance(SimulationFile.class);
			Unmarshaller unmarshaller = jc.createUnmarshaller();

			simFile = (SimulationFile) unmarshaller.unmarshal(is);

		} finally {
			try {
				is.close();
			} catch (final IOException ignore) {
			}
		}

		if (simFile == null) {
			LOG.error("Was not able to read input, aborting inference.");
			return;
		}
		long offsetMS;
		if (this.replication == null) {
			LOG.warn("Replication not found in DB: "
					+ getID().getModelID().getValue()
					+ ", starting default scenario...");
			this.replication = UseCaseScenario.Util.saveDefaultReplication(
					getBinder(),
					simFileURI,
					Amount.valueOf(cfg.getDuration().toStandardSeconds()
							.getSeconds(), SI.SECOND));
		} else {
			offsetMS = this.replication.getStartDate().longValue();
			LOG.info("Got replication from DB: " + this.replication.toJSON());
			// update the ReplicationConfig settings from DB values
			final long durationMS = this.replication.getDurationMS()
					.longValue();
			if (durationMS > 0)
				cfg.setProperty(ReplicationConfig.DURATION_KEY, new Duration(
						durationMS).toPeriod().toString());
			else
				LOG.warn("Illegal duration specified in DB or GUI: "
						+ this.replication.getDurationMS());

			if (offsetMS > 0) {
				cfg.setProperty(ReplicationConfig.OFFSET_KEY, new DateTime(
						offsetMS).toString());
			} else
				LOG.warn("Illegal offset specified in DB or GUI: "
						+ this.replication.getStartDate());
		}
		offsetMS = cfg.getOffset().getMillis();
		setReplication(SimStatus.PREPARING, 0);

		TUseCase simCase = simFile.getSimulations().getSimulationCase().get(0)
				.getUsecase();

		try {
			this.scenario = UseCaseScenario.Builder.fromXML(this.replication,
					simCase,
					getBinder().inject(RandomDistribution.Factory.class),
					getBinder().inject(ReplicatingCapability.class).getRNG())
					.build();
		} catch (final Exception e) {
			LOG.error("Problem loading scenario", e);
		}
		if (this.scenario == null)
			throw new NullPointerException("No Scenario loaded!");

		final ModelComponentIDFactory agentIDFactory = getBinder().inject(
				ModelComponentIDFactory.class);
		// persist processes for process mgmt agents
		for (String processTypeID : this.scenario.getProcessTypeIDs()) {
			LOG.warn("Persisting process type: " + processTypeID);
			ds.save(this.scenario.getProcess(processTypeID));
		}

		if (replication.getStartDate() == null)
			replication.setStartDate(offsetMS);

		for (AssemblyLine assemblyLine : this.scenario.getAssemblyLines()) {
			this.assemblyLineIDs.add(agentIDFactory.createAgentID(assemblyLine
					.getName()));
			this.assemblyLines.add(assemblyLine);
			ds.save(assemblyLine);

			final ResourceEvent createAgentAction = getResourceEventForNewAgent(
					agentIDFactory.createAgentID(assemblyLine.getName()),
					AssemblyLineResourceManagementOrganization.class,
					getBinder().inject(SimTimeFactory.class).create(
							(assemblyLine.getAvailableFromTime() == null) ? 0
									: assemblyLine.getAvailableFromTime(),TimeUnit.MILLIS));
			if (!this.agents.add(createAgentAction))
				LOG.warn("Problem adding assemblyLine: "
						+ assemblyLine.getName());
		}

		for (Material material : this.scenario.getMaterials()) {
			this.materialIDs.add(agentIDFactory.createAgentID(material
					.getName()));
			this.materials.add(material);
			ds.save(material);
			final ResourceEvent dCreateAgentAction = getResourceEventForNewAgent(
					agentIDFactory.createAgentID(material.getName()),
					MaterialResourceManagementOrganization.class,
					getBinder().inject(SimTimeFactory.class).create(
							(material.getAvailableFromTime() == null) ? 0
									: material.getAvailableFromTime(),TimeUnit.MILLIS));
			this.agents.add(dCreateAgentAction);
		}

		for (Person person : this.scenario.getPersons()) {
			this.personIDs.add(agentIDFactory.createAgentID(person.getName()));

			this.persons.add(person);
			ds.save(person);
			// Also re-save the assemblyLine the person enters
			if (person.getAtAssemblyLine() != null)
				ds.save(person.getAtAssemblyLine());
			final ResourceEvent createAgentAction = getResourceEventForNewAgent(
					agentIDFactory.createAgentID(person.getName()),
					PersonResourceManagementOrganization.class,
					getBinder().inject(SimTimeFactory.class).create(
							(person.getAvailableFromTime() == null) ? 0
									: person.getAvailableFromTime(), TimeUnit.MILLIS));
			this.agents.add(createAgentAction);
		}

		final SimTime duration = getBinder().inject(SimTimeFactory.class)
				.create(replication.getDurationMS(), TimeUnit.MILLIS);
		LOG.info("Sim repl duration: " + getBinder().inject(Period.class));

		getBinder().inject(ReplicatingCapability.class).getTimeUpdates()
				.subscribe(new Observer<SimTime>() {
					@Override
					public void onNext(final SimTime time) {
						if (replication.getStatus().equals(SimStatus.PREPARING))
							setReplication(SimStatus.RUNNING, 0);
						// HOLD the simulation for debugging purposes
						Double autoPause;
						try {
							autoPause = getBinder()
									.inject(ConfiguringCapability.class)
									.getProperty("autopause").getDouble();
							if (autoPause != null && autoPause > 0)
								if (time.isAfter(getBinder().inject(
										SimTimeFactory.class).create(autoPause,
										TimeUnit.DAYS))) {
									LOG.info("Paused replication for debugging puposes");
									getBinder().inject(
											ReplicatingCapability.class)
											.pause();
								}
						} catch (CoalaException e1) {
							LOG.trace("Simulation may continue because no auto-pause property could be retrieved: "
									+ e1.getMessage());
						}
						// ------------------------------------------
						LOG.trace("Time changed: " + time);
						final Number progressPerc =
						// (time.getValue().doubleValue() == 0.0) ? 0 :
						time.dividedBy(duration).doubleValue() * 100;
						if (replication.getProgress().doubleValue() != progressPerc
								.doubleValue())
							setReplication(null, progressPerc);

						if (replication.getStatus().equals(SimStatus.RUNNING)
								&& (progressPerc.doubleValue() >= 100 || time
										.isOnOrAfter(SimTime.ZERO.plus(8,
												TimeUnit.DAYS)))) {
							setReplication(SimStatus.FINISHED, 100);
							try {
								if (time.isOnOrAfter(SimTime.ZERO.plus(8,
										TimeUnit.DAYS)))
									EventExtrapolator.extrapolator(replication
											.getId(), SimTime.ZERO.plus(1,
											TimeUnit.DAYS), new SimDuration(7,
											TimeUnit.DAYS), new SimDuration(
											replication.getDurationMS(),
											TimeUnit.MILLIS));
							} catch (Exception e) {
								LOG.error(
										"failed to extrapolate results: "
												+ e.getMessage(), e);
							}
							System.exit(0); // FIXME Must be more elegant
											// eventualy
						}
					}

					@Override
					public void onCompleted() {
					}

					@Override
					public void onError(final Throwable t) {
						t.printStackTrace();
					}
				});
	}

	/** @see ScenarioManagementWorld#getReplication() */
	@Override
	public synchronized Replication getReplication() {
		return this.replication;
	}

	/** @see ScenarioManagementWorld#getCurrentAssemblyLineIDs() */
	@Override
	public Set<AgentID> getCurrentAssemblyLineIDs() {
		return Collections.unmodifiableSet(this.assemblyLineIDs);
	}

	/** @see ScenarioManagementWorld#getCurrentMaterialIDs() */
	@Override
	public Set<AgentID> getCurrentMaterialIDs() {
		return Collections.unmodifiableSet(this.materialIDs);
	}

	/** @see ScenarioManagementWorld#getCurrentPersonIDs() */
	@Override
	public Set<AgentID> getCurrentPersonIDs() {
		return Collections.unmodifiableSet(this.personIDs);
	}

	ReplaySubject<ScenarioManagementWorld.ResourceEvent> resourceEvents;

	private int currentResourceHash;


	/** @see ScenarioManagementWorld#onResources() */
	@Override
	public Observable<ResourceEvent> onResources() {
		if (resourceEvents == null)
			resourceEvents = ReplaySubject.create();
		for (ResourceEvent a : this.agents) {
			getBinder().inject(ReplicatingCapability.class).schedule(
					ProcedureCall.create(this, this, RESOURCE_AVAILABLE, a),
					Trigger.createAbsolute(a.getEventTime()));
			LOG.error("SCHEDULING: " + a.getResourceID() + " at "
					+ a.getEventTime());
		}
		return resourceEvents.asObservable();
	}

	public static final String RESOURCE_AVAILABLE = "RESOURCE_AVAILABLE";

	@Schedulable(RESOURCE_AVAILABLE)
	public void resourceAvailable(ResourceEvent e) {
		LOG.error("AVAILABLE: " + e.getResourceID() + " at " + e.getEventTime());
		resourceEvents.onNext(e);
		updateResourceStatusHash(getOwnerID().toString());
	}

	@Override
	public Iterable<AssemblyLine> getAssemblyLines() {
		return this.assemblyLines;
	}

	@Override
	public Iterable<Material> getMaterials() {
		return this.materials;
	}

	private static long processInstanceCount = 0;

	private static String PROCESS_INSTANCE_PREFIX = "processInstance";

	/** @see ScenarioManagementWorld#onProcessEvent() */
	@Override
	public Observable<ProcessEvent> onProcessEvent() {
		// TODO make dynamic using a ReplaySubject
		// FIXME: Added take(n) for testing purposes, needs to be removed for
		// production
		return Observable.from(this.scenario.getProcessTypeIDs()).map(
				new Func1<String, ProcessEvent>() {
					@Override
					public ProcessEvent call(final String processTypeID) {
						return new ProcessEvent() {
							@Override
							public String getProcessTypeID() {
								return processTypeID;
							}

							@Override
							public AgentID getProcessInstanceID() {
								return getBinder()
										.inject(ModelComponentIDFactory.class)
										.createAgentID(
												PROCESS_INSTANCE_PREFIX
														+ (processInstanceCount++));
							}

							@Override
							public ProcessEventType getEventType() {
								return ProcessEventType.REQUESTED;
							}
						};
					}
				});
	}

	/** @see ScenarioManagementWorld#getProcessStartTimeOfDayDist(String) */
	@Override
	public RandomDistribution<SimDuration> getProcessStartTimeOfDayDist(
			final String processTypeID) {
		return this.scenario.getProcessStartDelayDistribution(processTypeID);
	}

	public synchronized void setReplication(final SimStatus status,
			final Number progressPerc) {
		if (status != null)
			this.replication.setStatus(status);
		if (progressPerc != null)
			this.replication.setProgress(progressPerc);
		getBinder().inject(Datasource.class).update(this.replication);
	}

	@Override
	public Iterable<Person> getPersons() {
		return this.persons;
	}

	@Override
	public Observable<Integer> resourceStatusHash() {
		return this.resourceHash.asObservable();
	}

	@Override
	public void updateResourceStatusHash(String base) {
		final int hash = (base.hashCode()+""+getTime().hashCode()).hashCode();
		if (this.currentResourceHash != hash) {
			this.resourceHash.onNext(hash);
			this.currentResourceHash = hash;
		}
	}

	@Override
	public int getCurrentResourceStatusHash() {
		return currentResourceHash;
	}

}
