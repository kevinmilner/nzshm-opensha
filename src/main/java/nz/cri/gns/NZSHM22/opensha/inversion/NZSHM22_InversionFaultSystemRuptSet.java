package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;

import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

import java.util.concurrent.Callable;

/**
 * This class provides specialisatations needed to override some UCERF3 defaults
 * in the base class.
 *
 * @author chrisbc
 *
 */
public class NZSHM22_InversionFaultSystemRuptSet extends InversionFaultSystemRupSet {

	private static final long serialVersionUID = 1091962054533163866L;

	// overwrite isRupBelowMinMagsForSects from InversionFaultSystemRupSet
	private boolean[] isRupBelowMinMagsForSects;
	private double[] minMagForSectArray;
	protected static double minMagForSeismogenicRups = 6.0;

	/**
	 * Constructor which relies on the super-class implementation
	 *
	 * @param rupSet
	 * @param branch
	 */
	protected NZSHM22_InversionFaultSystemRuptSet (FaultSystemRupSet rupSet, U3LogicTreeBranch branch) {
	    super(rupSet, branch);
	}

	public static NZSHM22_InversionFaultSystemRuptSet fromSubduction(FaultSystemRupSet rupSet, U3LogicTreeBranch branch) {
		NZSHM22_InversionFaultSystemRuptSet result = new NZSHM22_InversionFaultSystemRuptSet(rupSet, branch);

		//overwrite behaviour of super class
        result.removeModuleInstances(FaultGridAssociations.class);
		result.removeModuleInstances(InversionTargetMFDs.class);
		result.offerAvailableModule(new Callable<NZSHM22_SubductionInversionTargetMFDs>() {
			@Override
			public NZSHM22_SubductionInversionTargetMFDs call() throws Exception {
				return new NZSHM22_SubductionInversionTargetMFDs(result);
			}
		}, NZSHM22_SubductionInversionTargetMFDs.class);
		return result;
	}

	public static NZSHM22_InversionFaultSystemRuptSet fromCrustal(FaultSystemRupSet rupSet, U3LogicTreeBranch branch){
		NZSHM22_InversionFaultSystemRuptSet result = new NZSHM22_InversionFaultSystemRuptSet(rupSet, branch);

		result.removeModuleInstances(InversionTargetMFDs.class);
		result.offerAvailableModule(new Callable<NZSHM22_CrustalInversionTargetMFDs>() {
			@Override
			public NZSHM22_CrustalInversionTargetMFDs call() throws Exception {
				return new NZSHM22_CrustalInversionTargetMFDs(result);
			}
		}, NZSHM22_CrustalInversionTargetMFDs.class);
		return result;
	}

	/**
	 * This returns the final minimum mag for a given fault section. This uses a
	 * generic version of computeMinSeismoMagForSections() instead of the UCERF3
	 * implementation.
	 *
	 * @param sectIndex
	 * @return
	 */
	@Override
	public synchronized double getFinalMinMagForSection(int sectIndex) {
		// FIXME
		throw new IllegalStateException("net yet refactored!");
//		if (minMagForSectArray == null) {
//			minMagForSectArray = NZSHM22_FaultSystemRupSetCalc.computeMinSeismoMagForSections(this,
//					minMagForSeismogenicRups);
//		}
//		return minMagForSectArray[sectIndex];
	}

	public NZSHM22_InversionFaultSystemRuptSet setInversionTargetMFDs(InversionTargetMFDs inversionMFDs) {
		removeModuleInstances(InversionTargetMFDs.class);
		addModule(inversionMFDs);
		return this;
	}

	public static void setMinMagForSeismogenicRups(double minMag){
		minMagForSeismogenicRups = minMag;
	}

	// this overrides the calculation for the ModSectMinMags module
    @Override
	protected double[] calcFinalMinMagForSections() {
		return NZSHM22_FaultSystemRupSetCalc.computeMinSeismoMagForSections(this,minMagForSeismogenicRups);
	}


}
