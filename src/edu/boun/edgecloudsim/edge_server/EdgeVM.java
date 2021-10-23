/*
 * Title:        EdgeCloudSim - EdgeVM
 * 
 * Description: 
 * EdgeVM adds vm type information over CloudSim's VM class
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_server;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.Vm;

import edu.boun.edgecloudsim.core.SimSettings;

public class EdgeVM extends Vm {
	private SimSettings.VM_TYPES type;
	private EdgeScheduler_Custom scheduler;
	
	private double remainTime[] = {0,0,0};
	
	public EdgeVM(int id, int userId, double mips, int numberOfPes, int ram,
			long bw, long size, String vmm, CloudletScheduler cloudletScheduler) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);

		type = SimSettings.VM_TYPES.EDGE_VM;
		scheduler = (EdgeScheduler_Custom) cloudletScheduler;
	}

	public SimSettings.VM_TYPES getVmType(){
		return type;
	}
	
	
	
	public double getRemainTime(int _class_) {
		return scheduler.getRemainTime(_class_);
		
	}
	
	public List<List<ResCloudlet>> getWaitingList(){
		return scheduler.getWaitingList();
	}
	
	

	/**
	 *  dynamically reconfigures the mips value of a  VM in CloudSim
	 * 
	 * @param mips new mips value for this VM.
	 */
	public void reconfigureMips(double mips){
		super.setMips(mips);
		super.getHost().getVmScheduler().deallocatePesForVm(this);
		
		List<Double> mipsShareAllocated = new ArrayList<Double>();
		for(int i= 0; i<getNumberOfPes(); i++)
			mipsShareAllocated.add(mips);

		super.getHost().getVmScheduler().allocatePesForVm(this, mipsShareAllocated);
	}
}
