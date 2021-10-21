package nz.cri.gns.NZSHM22.opensha.analysis;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.U3FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

public class NZSHM22_FaultSystemRupSetCalc extends FaultSystemRupSetCalc {

	/**
	 * Override the UCERF3 implementation which does something special when
	 * ID==Parkfield
	 * 
	 * @param fltSystRupSet
	 * @param systemWideMinSeismoMag
	 * @return
	 */
	public static double[] computeMinSeismoMagForSections(U3FaultSystemRupSet fltSystRupSet,
														  double systemWideMinSeismoMag) {
		double[] minMagForSect = new double[fltSystRupSet.getNumSections()];
		String prevParSectName = "junk_imp0ss!ble_fault_name_1067487@#";
		List<? extends FaultSection> sectDataList = fltSystRupSet.getFaultSectionDataList();

		// make map between parent section name and maximum magnitude (magForParSectMap)
		HashMap<String, Double> magForParSectMap = new HashMap<String, Double>();
		double maxMinSeismoMag = 0;
		double minMinSeismoMag = 0; // this is for testing
		for (int s = 0; s < sectDataList.size(); s++) {
			String parSectName = sectDataList.get(s).getParentSectionName();
            double minSeismoMag = fltSystRupSet.getMinMagForSection(s);
			if (!parSectName.equals(prevParSectName)) { // it's a new parent section
				// set the previous result
				if (!prevParSectName.equals("junk")) {
					magForParSectMap.put(prevParSectName, maxMinSeismoMag);
				}
				// reset maxMinSeismoMag & prevParSectName
				maxMinSeismoMag = minSeismoMag;
				minMinSeismoMag = minSeismoMag;
				prevParSectName = parSectName;

			} else {
				if (maxMinSeismoMag < minSeismoMag)
					maxMinSeismoMag = minSeismoMag;
				if (minMinSeismoMag > minSeismoMag)
					minMinSeismoMag = minSeismoMag;
			}
		}
		// do the last one:
		magForParSectMap.put(prevParSectName, maxMinSeismoMag);

		// for(String parName:magForParSectMap.keySet())
		// System.out.println(parName+"\t"+magForParSectMap.get(parName));

		// now set the value for each section in the array, giving a value of
		// systemWideMinMinSeismoMag
		// if the parent section value falls below this
		for (int s = 0; s < sectDataList.size(); s++) {
			double minMag = magForParSectMap.get(sectDataList.get(s).getParentSectionName());
			if (minMag > systemWideMinSeismoMag)
				minMagForSect[s] = minMag;
			else
				minMagForSect[s] = systemWideMinSeismoMag;
		}

		return minMagForSect;

//		// test result:
//		try {
//			FileWriter fw = new FileWriter("TestHereItIs");
//			for(int s=0; s< sectDataList.size();s++) {
//				String sectName = sectDataList.get(s).getSectionName();
//				double tempMag = magForParSectMap.get(sectDataList.get(s).getParentSectionName());
//				double origSlipRate = sectDataList.get(s).getOrigAveSlipRate();
//				double aseisSlipFactor = sectDataList.get(s).getAseismicSlipFactor();
//				fw.write(sectName+"\t"+getMinMagForSection(s)+"\t"+tempMag+"\t"+minMagForSect[s]+"\t"+origSlipRate+"\t"+aseisSlipFactor+"\n");
//			}
//			fw.close ();
//		}
//		catch (IOException e) {
//			System.out.println ("IO exception = " + e );
//		}

	}

	/**
	 * This gets the sub-seismogenic MFD for each fault section for the characteristic model,
	 * where each fault gets a GR up to just below the minimum seismogenic magnitude, with a total rate
	 * equal to the rate of events inside the fault section polygon (as determined by the
	 * spatialSeisPDF and tatal regional rate).
	 * @param fltSysRupSet
	 * @param spatialSeisPDF
	 * @param totalTargetGR
	 * @return
	 */
	public static ArrayList<GutenbergRichterMagFreqDist> getCharSubSeismoOnFaultMFD_forEachSection(
			InversionFaultSystemRupSet invRupSet,
			GriddedSeisUtils gridSeisUtils,
			GutenbergRichterMagFreqDist totalTargetGR) {
		return getCharSubSeismoOnFaultMFD_forEachSection(invRupSet, invRupSet.getModule(ModSectMinMags.class),
				gridSeisUtils, totalTargetGR);
	}


	/**
	 * This gets the sub-seismogenic MFD for each fault section for the characteristic model,
	 * where each fault gets a GR up to just below the minimum seismogenic magnitude, with a total rate
	 * equal to the rate of events inside the fault section polygon (as determined by the
	 * spatialSeisPDF and tatal regional rate).
	 * @param fltSysRupSet
	 * @param spatialSeisPDF
	 * @param totalTargetGR
	 * @return
	 */
	public static ArrayList<GutenbergRichterMagFreqDist> getCharSubSeismoOnFaultMFD_forEachSection(
			FaultSystemRupSet invRupSet,
			ModSectMinMags finalMinMags,
			GriddedSeisUtils gridSeisUtils,
			GutenbergRichterMagFreqDist totalTargetGR) {

		ArrayList<GutenbergRichterMagFreqDist> mfds = new ArrayList<GutenbergRichterMagFreqDist>();
		double totMgt5_rate = totalTargetGR.getCumRate(0);
		for(int s=0; s<invRupSet.getNumSections(); s++) {
			double sectRate = gridSeisUtils.pdfValForSection(s)*totMgt5_rate;
//			int mMaxIndex = totalTargetGR.getClosestXIndex(fltSysRupSet.getMinMagForSection(s))-1;	// subtract 1 to avoid overlap
			double upperMag = InversionFaultSystemRupSet.getUpperMagForSubseismoRuptures(finalMinMags.getMinMagForSection(s));
			int mMaxIndex = totalTargetGR.getXIndex(upperMag);
			if(mMaxIndex == -1) throw new RuntimeException("Problem Mmax: "
					+upperMag+"\t"+invRupSet.getFaultSectionData(s).getName()+"\tBRANCH: "+invRupSet.getModule(U3LogicTreeBranch.class));
			double mMax = totalTargetGR.getX(mMaxIndex); // rounded to nearest MFD value
//if(mMax<5.85)
//	System.out.println("PROBLEM SubSesMmax=\t"+mMax+"\tMinSeismoRupMag=\t"
//			+invRupSet.getFinalMinMagForSection(s)+"\t"+invRupSet.getFaultSectionData(s).getName());
			GutenbergRichterMagFreqDist tempOnFaultGR = new GutenbergRichterMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(),
					totalTargetGR.getDelta(), totalTargetGR.getMagLower(), mMax, 1.0, totalTargetGR.get_bValue());
			tempOnFaultGR.scaleToCumRate(0, sectRate);
			// check for NaN rates
			if(Double.isNaN(tempOnFaultGR.getTotalIncrRate())) {
				throw new RuntimeException("Bad MFD for section:\t"+s+"\t"+invRupSet.getFaultSectionData(s).getName()+
						"\tsectRate="+sectRate+"\tgridSeisUtils.pdfValForSection(s) = "+gridSeisUtils.pdfValForSection(s)+"\tmMax = "+mMax);
			}
			mfds.add(tempOnFaultGR);
		}
		return mfds;
	}

	/**
	 * This computes a tri-linear MFD for the off-fault MFD on the characteristic branch
	 *
	 * @param totalTargetGR - the total target MFD
	 * @param totOnFaultMgt5_Rate - the rate of on-fault events at Mge5
	 * @param mMinSeismoOnFault - the average minimum magnitude for seismogenic on-fault ruptures
	 * @param mMaxOffFault - the maximum magnitude for off-fault events.
	 * @return
	 */
	public static IncrementalMagFreqDist getTriLinearCharOffFaultTargetMFD(GutenbergRichterMagFreqDist totalTargetGR, double totOnFaultMgt5_Rate,
																		   double mMinSeismoOnFault, double mMaxOffFault) {

		int mMinSeismoOnFaultIndex = totalTargetGR.getClosestXIndex(mMinSeismoOnFault);
		int mMaxOffFaultIndex = totalTargetGR.getClosestXIndex(mMaxOffFault);

		double offFaultMgt5_Rate = totalTargetGR.getCumRate(5.05) - totOnFaultMgt5_Rate;

		// rate corrections since final MFDs are not perfect GRs (found by hand)
		double onCorr = 0.98;
		double offCorr = 1.01;

		// create a temp GR with on-fault rate
		GutenbergRichterMagFreqDist tempOnFaultGR = new GutenbergRichterMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(),
				totalTargetGR.getDelta(), totalTargetGR.getMagLower(), totalTargetGR.getMagUpper(), 1.0, totalTargetGR.get_bValue());
		tempOnFaultGR.scaleToCumRate(5.05, totOnFaultMgt5_Rate*onCorr);

		// create a temp GR with off-fault rate
		GutenbergRichterMagFreqDist tempOffFaultGR = new GutenbergRichterMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(),
				totalTargetGR.getDelta(), totalTargetGR.getMagLower(), totalTargetGR.getMagUpper(), 1.0, totalTargetGR.get_bValue());
		tempOffFaultGR.scaleToCumRate(5.05, offFaultMgt5_Rate*offCorr);

		// now create the desired MFDs
		IncrementalMagFreqDist onFaultMFD = new IncrementalMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), totalTargetGR.getDelta());
		IncrementalMagFreqDist offFaultMFD = new IncrementalMagFreqDist(totalTargetGR.getMinX(), totalTargetGR.size(), totalTargetGR.getDelta());
		for(int i=0; i<mMinSeismoOnFaultIndex;i++) {
			onFaultMFD.set(i,tempOnFaultGR.getY(i));
			offFaultMFD.set(i,tempOffFaultGR.getY(i));
		}
		for(int i=mMinSeismoOnFaultIndex; i<=mMaxOffFaultIndex+1; i++) {
			double wtOnTotRate = (double)(i-mMinSeismoOnFaultIndex)/(double)((mMaxOffFaultIndex+1)-mMinSeismoOnFaultIndex); // starts at zero and builds
			double wtOnFaultRate = 1.0-wtOnTotRate;
			// way 1
//			double onFltRate = wtOnFaultRate*tempOnFaultGR.getY(i)+wtOnTotRate*totalTargetGR.getY(i);
//			onFaultMFD.set(i,onFltRate);
//			offFaultMFD.set(i,totalTargetGR.getY(i)-onFltRate);
			// way 2 (same as Way 1)
//			offFaultMFD.set(i,tempOffFaultGR.getY(i)*wtOnFaultRate);
//			onFaultMFD.set(i,totalTargetGR.getY(i)-offFaultMFD.getY(i));
			// way 3
			double onFltRate = Math.pow(10,wtOnFaultRate*Math.log10(tempOnFaultGR.getY(i)) + wtOnTotRate*Math.log10(totalTargetGR.getY(i)));
			onFaultMFD.set(i,onFltRate);
			offFaultMFD.set(i,totalTargetGR.getY(i)-onFltRate);
			if(offFaultMFD.getY(i) < 0 ) offFaultMFD.set(i,0); // numerical precision issue at last point if mMaxOffFault = totalTargetGR.getMagUpper()
		}
		for(int i=mMaxOffFaultIndex+1; i<totalTargetGR.size(); i++) {
			onFaultMFD.set(i,totalTargetGR.getY(i));
			offFaultMFD.set(i,0);
		}

		onFaultMFD.setName("onFaultMFD");
		onFaultMFD.setInfo("(rate(M>=5)="+(float)onFaultMFD.getCumRate(5.05)+"; totMoRate="+onFaultMFD.getTotalMomentRate()+")");
		offFaultMFD.setName("offFaultMFD");
		offFaultMFD.setInfo("(rate(M>=5)="+(float)offFaultMFD.getCumRate(5.05)+"; totMoRate="+offFaultMFD.getTotalMomentRate()+")");


//		// TESTS
//		System.out.println("\nInputs:\n");
//		System.out.println("\ttotOnFaultMgt5_Rate = "+(float)totOnFaultMgt5_Rate);
//		double totRate = totalTargetGR.getCumRate(5.05);
//		double totMoRate = totalTargetGR.getTotalMomentRate();
//		System.out.println("\ttotalTargetGR.getCumRate(5.05) = "+(float)totRate);
//		System.out.println("\ttotalTargetGR.getTotalMomentRate() = "+(float)totMoRate);
//		System.out.println("\tmMinSeismoOnFault="+(float)mMinSeismoOnFault);
//		System.out.println("\tmMaxOffFault="+(float)mMaxOffFault);
//
//		System.out.println("\nResults:\n");
//		System.out.println("\tonFaultMFD.getCumRate(5.05) = "+(float)onFaultMFD.getCumRate(5.05)+"\tfraction="+((float)(onFaultMFD.getCumRate(5.05)/totRate)));
//		System.out.println("\toffFaultMFD.getCumRate(5.05) = "+(float)offFaultMFD.getCumRate(5.05)+"\tfraction="+((float)(offFaultMFD.getCumRate(5.05)/totRate)));
//		System.out.println("\ttotal implied Rate(>=5.05) = "+(float)(offFaultMFD.getCumRate(5.05)+onFaultMFD.getCumRate(5.05)));
//		System.out.println("\tonFaultMFD.getTotalMomentRate() = "+(float)onFaultMFD.getTotalMomentRate()+"\tfraction="+((float)(onFaultMFD.getTotalMomentRate()/totMoRate)));
//		System.out.println("\toffFaultMFD.getTotalMomentRate() = "+(float)offFaultMFD.getTotalMomentRate()+"\tfraction="+((float)(offFaultMFD.getTotalMomentRate()/totMoRate)));
//		System.out.println("\nTests (all should be close to 1.0):\n");
//		System.out.println("\tTotMoRate: "+(float)(totMoRate/(onFaultMFD.getTotalMomentRate()+offFaultMFD.getTotalMomentRate()))+"\t(totMoRate/(onFaultMFD.getTotalMomentRate()+offFaultMFD.getTotalMomentRate()))");
//		System.out.println("\tTotCumRate: "+(float)(totRate/(onFaultMFD.getCumRate(5.05)+offFaultMFD.getCumRate(5.05)))+"\t(totRate/(onFaultMFD.getCumRate(5.05)+offFaultMFD.getCumRate(5.05)))");
//		System.out.println("\tOnFaultCumRate: "+(float)(totOnFaultMgt5_Rate/onFaultMFD.getCumRate(5.05))+"\t(totOnFaultMgt5_Rate/onFaultMFD.getCumRate(5.05))");
//		System.out.println("\tOffFaultCumRate: "+(float)((totRate-totOnFaultMgt5_Rate)/+offFaultMFD.getCumRate(5.05))+"\t((totRate-totOnFaultMgt5_Rate)/+offFaultMFD.getCumRate(5.05))");
//
//		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
//		funcs.add(totalTargetGR);
//		funcs.add(offFaultMFD);
//		funcs.add(onFaultMFD);
////		funcs.add(totalTargetGR.getCumRateDistWithOffset());
////		funcs.add(offFaultMFD.getCumRateDistWithOffset());
////		funcs.add(onFaultMFD.getCumRateDistWithOffset());
//		GraphWindow graph = new GraphWindow(funcs, "MFDs");
//		graph.setX_AxisRange(5, 9);
//		graph.setY_AxisRange(1e-5, 10);
//		graph.setYLog(true);
//		graph.setX_AxisLabel("Mag");
//		graph.setY_AxisLabel("Rate (per year)");
//		graph.setPlotLabelFontSize(18);
//		graph.setAxisLabelFontSize(16);
//		graph.setTickLabelFontSize(14);

//		IncrementalMagFreqDist[] mfds = {onFaultMFD,offFaultMFD};
		return offFaultMFD;
	}

}
