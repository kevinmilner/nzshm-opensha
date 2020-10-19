package nz.cri.gns.NSHM.opensha.ruptures;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.FaultIdFilter;
import org.dom4j.DocumentException;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MinSectsPerParentFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ConnectionPointsPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.UCERF3ClusterPermuationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.DownDipSubSectBuilder;
import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.DownDipTestPermutationStrategy;
import nz.cri.gns.NSHM.opensha.util.FaultSectionList;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

/**
 * Builds opensha SlipAlongRuptureModelRupSet rupture sets 
 * using NZ NSHM configurations for:
 *  - plausability
 *  - rupture permutation Strategy (with different strategies available for test purposes)
 */
public class NSHMRuptureSetBuilder {

	static DownDipSubSectBuilder downDipBuilder;	
	List<ClusterRupture> ruptures;
	FaultSectionList subSections;
	PlausibilityConfiguration plausibilityConfig;
	ClusterRuptureBuilder builder;
	
	Set<Integer> faultIds;
	FaultIdFilter.FilterType faultIdfilterType = null;
	
	double maxSubSectionLength = 0.5; // maximum sub section length (in units of DDW)
	double maxDistance = 5; // max distance for linking multi fault ruptures, km
	long maxFaultSections = 100000; // maximum fault ruptures to process
	long skipFaultSections = 0; // skip n fault ruptures, default 0"
	int numThreads = Runtime.getRuntime().availableProcessors(); // use all available processors
	int minSubSectsPerParent = 2; // 2 are required for UCERf3 azimuth calcs
	float maxAzimuthChange = 60;
	float maxTotalAzimuthChange = 60;
	float maxCumulativeAzimuthChange = 560;
	RupturePermutationStrategy permutationStrategyClass = RupturePermutationStrategy.DOWNDIP;
	
	public enum RupturePermutationStrategy {
		DOWNDIP, UCERF3, POINTS,
	}

	/**
	 * Constructs a new NSHMRuptureSetBuilder with the default NSHM configuration.
	 */
	public NSHMRuptureSetBuilder () {
		FaultSection interfaceParentSection = new FaultSectionPrefData();
		interfaceParentSection.setSectionId(10000);
		downDipBuilder = new DownDipSubSectBuilder(interfaceParentSection);
	}
	
	/**
	 * For testing of specific ruptures
	 *
	 * @param filterType The behaviour of the filter. See FaultIdFilter.
	 * @param faultIds A set of fault section integer ids.
	 * @return NSHMRuptureSetBuilder the builder
	 */
	public NSHMRuptureSetBuilder setFaultIdFilter(FaultIdFilter.FilterType filterType, Set<Integer> faultIds) {
		this.faultIds = faultIds;
		this.faultIdfilterType = filterType;
		return this;
	}
	/**
	 * Sets the maximum jump distance allowed between fault sections
	 * 
	 * @param maxDistance km
	 * @return NSHMRuptureSetBuilder the builder
	 */
	public NSHMRuptureSetBuilder setMaxJumpDistance(double maxDistance) {
		this.maxDistance = maxDistance;
		return this;
	}
	/**
	 * Used for testing only!
	 * 
	 * @param maxFaultSections the maximum number of fault sections to be processed.
	 * @return NSHMRuptureSetBuilder the builder
	 */
	public NSHMRuptureSetBuilder setMaxFaultSections(int maxFaultSections) {
		this.maxFaultSections = maxFaultSections;
		return this;
	}
	
	/**
	 * Used for testing only!
	 * 
	 * @param skipFaultSections sets the number fault sections to be skipped.
	 * @return NSHMRuptureSetBuilder the builder
	 */
	public NSHMRuptureSetBuilder setSkipFaultSections(int skipFaultSections) {
		this.skipFaultSections = skipFaultSections;
		return this;
	}
	
	/**
	 * 
	 * @param minSubSectsPerParent sets the minimum subsections per parent, 2 is standard as per UCERF3
	 * @return NSHMRuptureSetBuilder the builder
	 */
	public NSHMRuptureSetBuilder setMinSubSectsPerParent(int minSubSectsPerParent) {
		this.minSubSectsPerParent = minSubSectsPerParent;
		return this;
	}

	/**
	 * Sets the ratio of relative to DownDipWidth (DDW) that is used to calculate subsection lengths.
	 * 
	 * However, if fault sections are very short, then the minSubSectsPerParent may still force shorter sections 
	 * to be built.
	 * 
	 * @param maxSubSectionLength defaults to 0.5, meaning the desired minimum length is half of the DDW. 
	 * @return NSHMRuptureSetBuilder the builder
	 */
	public NSHMRuptureSetBuilder setMaxSubSectionLength(double maxSubSectionLength) {
		this.maxSubSectionLength = maxSubSectionLength;
		return this;
	}
	
	/**
	 * @param permutationStrategyClass sets the rupture permuation strategy implementation
	 * @return NSHMRuptureSetBuilder the builder
	 */
	public NSHMRuptureSetBuilder setPermutationStrategy(RupturePermutationStrategy permutationStrategyClass) {
		this.permutationStrategyClass = permutationStrategyClass;
		return this;
	}
	
	/**
	 * Some internal classes support parallelisation.
	 *  
	 * @param numThreads sets munber of threads to be used. 
	 * @return NSHMRuptureSetBuilder the builder
	 */
	public NSHMRuptureSetBuilder setNumThreads(int numThreads) {
		this.numThreads = numThreads;
		return this;
	}

	public NSHMRuptureSetBuilder setMaxAzimuthChange(float maxAzimuthChange){
		this.maxAzimuthChange = maxAzimuthChange;
		return this;
	}
	public NSHMRuptureSetBuilder setMaxTotalAzimuthChange(float maxTotalAzimuthChange){
		this.maxTotalAzimuthChange = maxTotalAzimuthChange;
		return this;
	}
	public NSHMRuptureSetBuilder setMaxCumulativeAzimuthChange(float maxCumulativeAzimuthChange){
		this.maxCumulativeAzimuthChange = maxCumulativeAzimuthChange;
		return this;
	}


	
	/**
	 * @param permutationStrategyClass which strategy to choose
	 * @return a ClusterPermutationStrategy object
	 */
	private ClusterPermutationStrategy createPermutationStrategy(RupturePermutationStrategy permutationStrategyClass) {
		ClusterPermutationStrategy permutationStrategy = null;
		switch (permutationStrategyClass) {
			case DOWNDIP:
				/* for down dip creates rectangular permutations to speed up rupture building
				*  for crustal , it uses something like UCERF3
				*/   
				permutationStrategy = new DownDipTestPermutationStrategy(downDipBuilder);
				break;
			case POINTS:
				// creates ruptures in blocks defined by the connection points between clusters
				permutationStrategy = new ConnectionPointsPermutationStrategy();
				break;
			case UCERF3:
				// creates ruptures covering the incremental permutations of sub-sections in each cluster 
				permutationStrategy = new UCERF3ClusterPermuationStrategy();
				break;
		}
		return permutationStrategy;
	}

	public SlipAlongRuptureModelRupSet buildRuptureSet(String fsdFileName) throws DocumentException, IOException {
		File fsdFile = new File(fsdFileName);
		return buildRuptureSet(fsdFile);
	}
	
	/**
	 * Builds an NSHM rupture set according to the configuration.
	 * 
	 * @param fsdFile the XML FalustSection data file containing source fault information
	 * @return a SlipAlongRuptureModelRupSet built according to the configuration from the input fsdFile
	 * @throws DocumentException
	 * @throws IOException
	 */
	public SlipAlongRuptureModelRupSet buildRuptureSet(File fsdFile) throws DocumentException, IOException {
		
		// load in the fault section data ("parent sections")
		FaultSectionList fsd = FaultSectionList.fromList(FaultModels.loadStoredFaultSections(fsdFile));
		System.out.println("Fault model has "+fsd.size()+" fault sections");
		
		if (maxFaultSections < 1000 || skipFaultSections > 0) {
			final long endSection = maxFaultSections + skipFaultSections;
			final long skipSections = skipFaultSections;
			fsd.removeIf(section -> section.getSectionId() >= endSection || section.getSectionId() < skipSections);
			System.out.println("Fault model now has "+fsd.size()+" fault sections");
		}
		
		// build the subsections
		subSections = new FaultSectionList(fsd);
		for (FaultSection parentSect : fsd) {
			double ddw = parentSect.getOrigDownDipWidth();
			double maxSectLength = ddw*maxSubSectionLength;
			System.out.println("Get subSections in "+parentSect.getName());
			// the "2" here sets a minimum number of sub sections
			List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength, subSections.getSafeId(), 2);
			getSubSections().addAll(newSubSects);
			System.out.println("Produced "+newSubSects.size()+" subSections in "+parentSect.getName());
		}		
		System.out.println(subSections.size()+" Sub Sections");
	
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSections);
		JumpAzimuthChangeFilter.AzimuthCalc azimuthCalc = new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc);
		
		// connection strategy: parent faults connect at closest point, and only when dist <=5 km
		ClusterConnectionStrategy connectionStrategy = new DistCutoffClosestSectClusterConnectionStrategy(subSections, distAzCalc, maxDistance);
		System.out.println("Built connectionStrategy");
		
		int maxNumSplays = 0; // don't allow any splays

		PlausibilityConfiguration.Builder configBuilder =
				PlausibilityConfiguration.builder(connectionStrategy, distAzCalc)
						.maxSplays(maxNumSplays)
						.add(new JumpAzimuthChangeFilter(azimuthCalc, maxAzimuthChange))
						.add(new TotalAzimuthChangeFilter(azimuthCalc, maxTotalAzimuthChange, true, true))
						.add(new CumulativeAzimuthChangeFilter(azimuthCalc, maxCumulativeAzimuthChange))
						.add(new MinSectsPerParentFilter(minSubSectsPerParent, true, true, connectionStrategy));
		if (faultIdfilterType != null){
			configBuilder.add(FaultIdFilter.create(faultIdfilterType, faultIds));
		}
		plausibilityConfig = configBuilder.build();
		System.out.println("Built PlausibilityConfiguration");
		
		// Builder can now proceed using the clusters and all the filters...
		builder = new ClusterRuptureBuilder(getPlausibilityConfig());
		System.out.println("initialised ClusterRuptureBuilder");
		
		ClusterPermutationStrategy permutationStrategy = createPermutationStrategy(permutationStrategyClass);
				
		ruptures = getBuilder().build(permutationStrategy, numThreads);
		System.out.println("Built "+ruptures.size()+" total ruptures");
		
		NSHMSlipEnabledRuptureSet rupSet = new NSHMSlipEnabledRuptureSet(ruptures, subSections,
				ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.UNIFORM);
		rupSet.setPlausibilityConfiguration(getPlausibilityConfig());
		return rupSet;
	}

	/**
	 * @return the ruptures
	 */
	public List<ClusterRupture> getRuptures() {
		return ruptures;
	}

	/**
	 * @return the subSections
	 */
	public FaultSectionList getSubSections() {
		return subSections;
	}

	/**
	 * @return the plausabilityConfig
	 */
	public PlausibilityConfiguration getPlausibilityConfig() {
		return plausibilityConfig;
	}

	/**
	 * @return the builder
	 */
	public ClusterRuptureBuilder getBuilder() {
		return builder;
	}			
}