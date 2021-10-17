package edu.boun.edgecloudsim.edge_server;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.edge_client.Task_Custom;

public class EdgeScheduler_Custom extends CloudletScheduler {
	/** The number of PEs currently available for the VM using the scheduler,
         * according to the mips share provided to it by
         * {@link #updateVmProcessing(double, java.util.List)} method. */
	protected int currentCPUs;
	protected ArrayList<ArrayList<ResCloudlet>> taskMap;

	/**
	 * Creates a new CloudletSchedulerTimeShared object. This method must be invoked before starting
	 * the actual simulation.
	 * 
	 * @pre $none
	 * @post $none
	 */
	public EdgeScheduler_Custom() {
		super();
		currentCPUs = 0;
		taskMap = new ArrayList<>();
		for(int i = 0; i < 4; i++) {
			ArrayList<ResCloudlet> taskList = new ArrayList<ResCloudlet>();
			taskMap.add(taskList);
		}
	}

	@Override
	public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
		setCurrentMipsShare(mipsShare);
		double timeSpam = currentTime - getPreviousTime();

		for (ResCloudlet rcl : getCloudletExecList()) {
			Task_Custom tc = (Task_Custom)rcl.getCloudlet();
			rcl.updateCloudletFinishedSoFar((long) (tc.getAllocatedReousrce() * timeSpam * rcl.getNumberOfPes() * Consts.MILLION));
		}

		if (getCloudletExecList().size() == 0) {
			setPreviousTime(currentTime);
			return 0.0;
		}

		// check finished cloudlets
		double nextEvent = Double.MAX_VALUE;
		int finished = 0;
		List<ResCloudlet> toRemove = new ArrayList<ResCloudlet>();
		for (ResCloudlet rcl : getCloudletExecList()) {
			long remainingLength = rcl.getRemainingCloudletLength();
			if (remainingLength == 0) {// finished: remove from the list
				for (int i = 0; i < taskMap.size(); i++) {
					if(taskMap.get(i).contains(rcl)) {
						taskMap.get(i).remove(rcl);
						break;
					}
				}
				toRemove.add(rcl);
				finished++;
				cloudletFinish(rcl);
			}
		}
		getCloudletExecList().removeAll(toRemove);
		
		if (!getCloudletWaitingList().isEmpty()) {
			if(SimManager.getInstance().getOrchestratorPolicy().equals("PROPOSED")) {
				//
				toRemove.clear();
				for(int k = 0; k < getCloudletWaitingList().size(); k ++) {
					ResCloudlet rc = getCloudletWaitingList().get(k);
					double minUse = mipsShare.get(0);
					int index = 0;
					double availResource = 0;
					
					for(int i = 0; i < taskMap.size(); i++) {
						double usage = 0;
						
						for(ResCloudlet rcl : taskMap.get(i)) {
							Task_Custom tc = (Task_Custom)rcl.getCloudlet();
							usage += tc.getAllocatedReousrce();
						}
						
						if(usage < minUse) {
							minUse = usage;
							index = i;
						}
					}
					
					availResource = mipsShare.get(0) - minUse;
					double minRemainLength = 0;
					double minallocResource = 1;
					
					if(taskMap.get(index).size() > 0) {
						minRemainLength = Double.MAX_VALUE;
						for(ResCloudlet rcl : taskMap.get(index)) {
							Task_Custom tc = (Task_Custom)rcl.getCloudlet();
							if(rcl.getRemainingCloudletLength() < minRemainLength) {
								minRemainLength = rcl.getRemainingCloudletLength();
								minallocResource = tc.getAllocatedReousrce();
							}
						}
					}
					
					if((rc.getCloudletLength() / availResource) < 
							(rc.getCloudletLength() / (availResource + minallocResource) + minRemainLength / minallocResource)) {
						Task_Custom tc = (Task_Custom)rc.getCloudlet();
						double time = Math.max(0.5 - (CloudSim.clock() - tc.getCreationTime()), CloudSim.clock() - tc.getCreationTime());
						double requireResource = rc.getCloudletLength() / time;
						
						if(availResource >= requireResource) {
							double remainResource = availResource - requireResource;
							ResCloudlet rc2 = k != getCloudletWaitingList().size() ? getCloudletWaitingList().get(k+1) : null;
							double rc2Require = 0;
							if(rc2 != null) {
								Task_Custom tc2 = (Task_Custom)rc2.getCloudlet();
								rc2Require = rc2.getCloudletLength() / (Math.max(0.5 - (CloudSim.clock() - tc2.getCreationTime()), CloudSim.clock() - tc2.getCreationTime()));
							}
							
							double discount = Math.sqrt(Math.abs((requireResource - rc2Require) / remainResource));
							
							for(double j = remainResource; j > 0; j-=10) {
								double alloc = j * (1 + discount);
								
								if(alloc <= remainResource) {
									tc.setAllocationResource(j);
								}
							}
							
							rc.setCloudletStatus(Cloudlet.INEXEC);
							getCloudletExecList().add(rc);
							toRemove.add(rc);
						}else {
							tc.setAllocationResource(availResource);
							rc.setCloudletStatus(Cloudlet.INEXEC);
							getCloudletExecList().add(rc);
							toRemove.add(rc);
						}
					}else {
						break;
					}
				}
			}else {
				toRemove.clear();
				for(ResCloudlet rcl : getCloudletWaitingList()) {
					if(rcl == null) break;
					Task_Custom tc = (Task_Custom)rcl.getCloudlet();
					
					double minUsage = mipsShare.get(0);
					int index = 0;
					for(int j = 0; j < taskMap.size(); j++) {
						double usage = 0;
						for(ResCloudlet rc : taskMap.get(j)){
							Task_Custom t = (Task_Custom)rc.getCloudlet();
							usage += t.getAllocatedReousrce();
						}
						
						if(minUsage > usage) {
							minUsage = usage;
							index = j;
						}
					}
					
					if (mipsShare.get(index) - minUsage >= tc.getAllocatedReousrce()) {
						rcl.setCloudletStatus(Cloudlet.INEXEC);
						for(int k = 0; k < rcl.getNumberOfPes(); k++) {
							rcl.setMachineAndPeId(0, k);
						}
						getCloudletExecList().add(rcl);
						taskMap.get(index).add(rcl);
						toRemove.add(rcl);
					} else {
						break;
					}
				}
			}
			getCloudletWaitingList().removeAll(toRemove);
		}

		// estimate finish time of cloudlets
		for (ResCloudlet rcl : getCloudletExecList()) {
			Task_Custom tc = (Task_Custom)rcl.getCloudlet();
			double estimatedFinishTime = currentTime
					+ (rcl.getRemainingCloudletLength() / (tc.getAllocatedReousrce() * rcl.getNumberOfPes()));
			if (estimatedFinishTime - currentTime < CloudSim.getMinTimeBetweenEvents()) {
				estimatedFinishTime = currentTime + CloudSim.getMinTimeBetweenEvents();
			}

			if (estimatedFinishTime < nextEvent) {
				nextEvent = estimatedFinishTime;
			}
		}

		setPreviousTime(currentTime);
		return nextEvent;
	}

	/**
	 * Gets the individual MIPS capacity available for each PE available for the scheduler,
         * considering that all PEs have the same capacity.
	 * 
	 * @param mipsShare list with MIPS share of each PE available to the scheduler
	 * @return the capacity of each PE
	 */
	protected double getCapacity(List<Double> mipsShare, ResCloudlet rcl) {
		int index = 0;
		
		for(int i = 0; i < taskMap.size(); i++) {
			if(taskMap.get(i).contains(rcl)) {
				index = i;
				break;
			}
		}
		
		return mipsShare.get(index)/taskMap.get(index).size();
	}

	@Override
	public Cloudlet cloudletCancel(int cloudletId) {
		// First, looks in the finished queue
		for (ResCloudlet rcl : getCloudletFinishedList()) {
			if (rcl.getCloudletId() == cloudletId) {
				getCloudletFinishedList().remove(rcl);
				return rcl.getCloudlet();
			}
		}

		// Then searches in the exec list
		for (ResCloudlet rcl : getCloudletExecList()) {
			if (rcl.getCloudletId() == cloudletId) {
				getCloudletExecList().remove(rcl);
				if (rcl.getRemainingCloudletLength() == 0) {
					cloudletFinish(rcl);
				} else {
					rcl.setCloudletStatus(Cloudlet.CANCELED);
				}
				return rcl.getCloudlet();
			}
		}

		// Now, looks in the paused queue
		for (ResCloudlet rcl : getCloudletPausedList()) {
			if (rcl.getCloudletId() == cloudletId) {
				getCloudletPausedList().remove(rcl);
				return rcl.getCloudlet();
			}
		}

		// Finally, looks in the waiting list
		for (ResCloudlet rcl : getCloudletWaitingList()) {
			if (rcl.getCloudletId() == cloudletId) {
				rcl.setCloudletStatus(Cloudlet.CANCELED);
				getCloudletWaitingList().remove(rcl);
				return rcl.getCloudlet();
			}
		}

		return null;
	}

	@Override
	public boolean cloudletPause(int cloudletId) {
		boolean found = false;
		int position = 0;

		// first, looks for the cloudlet in the exec list
		for (ResCloudlet rcl : getCloudletExecList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			// moves to the paused list
			ResCloudlet rgl = getCloudletExecList().remove(position);
			if (rgl.getRemainingCloudletLength() == 0) {
				cloudletFinish(rgl);
			} else {
				rgl.setCloudletStatus(Cloudlet.PAUSED);
				getCloudletPausedList().add(rgl);
			}
			return true;

		}

		// now, look for the cloudlet in the waiting list
		position = 0;
		found = false;
		for (ResCloudlet rcl : getCloudletWaitingList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			// moves to the paused list
			ResCloudlet rgl = getCloudletWaitingList().remove(position);
			if (rgl.getRemainingCloudletLength() == 0) {
				cloudletFinish(rgl);
			} else {
				rgl.setCloudletStatus(Cloudlet.PAUSED);
				getCloudletPausedList().add(rgl);
			}
			return true;

		}

		return false;
	}

	@Override
	public void cloudletFinish(ResCloudlet rcl) {
		rcl.setCloudletStatus(Cloudlet.SUCCESS);
		rcl.finalizeCloudlet();
		
		// find cloudlet
		for(int i = 0; i < taskMap.size(); i++) {
			if(taskMap.contains(rcl)) {
				taskMap.remove(rcl);
			}
		}
		
		getCloudletFinishedList().add(rcl);
	}

	@Override
	public double cloudletResume(int cloudletId) {
		boolean found = false;
		int position = 0;

		// look for the cloudlet in the paused list
		for (ResCloudlet rcl : getCloudletPausedList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			ResCloudlet rcl = getCloudletPausedList().remove(position);
			Task_Custom tc = (Task_Custom)rcl.getCloudlet();
			List<Double> mipsShare = getCurrentMipsShare();
			
			double minUsage = Double.MAX_VALUE;
			for(ResCloudlet rc : taskMap.get(0)) {
				Task_Custom t = (Task_Custom)rc.getCloudlet();
				minUsage += t.getAllocatedReousrce();
			}
			int index = 0;
			for(int i = 1; i < taskMap.size(); i++) {
				double usage = 0;
				for(ResCloudlet rc : taskMap.get(index)) {
					Task_Custom t = (Task_Custom)rc.getCloudlet();
					usage += t.getAllocatedReousrce();
				}
				
				if(minUsage > usage) {
					minUsage = usage;
					index = i;
				}
			}

			// it can go to the exec list
			if (mipsShare.get(index) - minUsage >= tc.getAllocatedReousrce()) {
				rcl.setCloudletStatus(Cloudlet.INEXEC);
				for (int i = 0; i < rcl.getNumberOfPes(); i++) {
					rcl.setMachineAndPeId(0, i);
				}

				long size = rcl.getRemainingCloudletLength();
				size *= rcl.getNumberOfPes();
				rcl.getCloudlet().setCloudletLength(size);

				getCloudletExecList().add(rcl);
				taskMap.get(index).add(rcl);

				// calculate the expected time for cloudlet completion

				long remainingLength = rcl.getRemainingCloudletLength();
				double estimatedFinishTime = CloudSim.clock()
						+ (remainingLength / (tc.getAllocatedReousrce() * rcl.getNumberOfPes()));

				return estimatedFinishTime;
			} else {// no enough free PEs: go to the waiting queue
				rcl.setCloudletStatus(Cloudlet.QUEUED);

				long size = rcl.getRemainingCloudletLength();
				size *= rcl.getNumberOfPes();
				rcl.getCloudlet().setCloudletLength(size);

				getCloudletWaitingList().add(rcl);
				return 0.0;
			}

		}

		// not found in the paused list: either it is in in the queue, executing or not exist
		return 0.0;

	}

	@Override
	public double cloudletSubmit(Cloudlet cloudlet, double fileTransferTime) {
		ResCloudlet rcl = new ResCloudlet(cloudlet);
		List<Double> mipsShare = getCurrentMipsShare();
		Task_Custom task = (Task_Custom)cloudlet;
		for (int i = 0; i < cloudlet.getNumberOfPes(); i++) {
			rcl.setMachineAndPeId(0, i);
		}
		
		if(SimManager.getInstance().getOrchestratorPolicy().equals("PROPOSED")) {
			// calculate require resource
			double minUse = mipsShare.get(0);
			int index = 0;
			for(int i = 0; i < taskMap.size(); i++) {
				double usageMips = 0;
				for(ResCloudlet rc : taskMap.get(i)) {
					Task_Custom tc = (Task_Custom)rc.getCloudlet();
					usageMips+= tc.getAllocatedReousrce();
				}
				if(minUse > usageMips) {
					minUse = usageMips;
					index = i;
				}
			}
			
			double minRemainLength = 0;
			double minResource = 0;
			if(taskMap.get(index).size() > 0) {
				minRemainLength = Double.MAX_VALUE;
				for(ResCloudlet rc : taskMap.get(index)) {
					if(rc.getRemainingCloudletLength() < minRemainLength) {
						Task_Custom tc = (Task_Custom)rc.getCloudlet();
						minRemainLength = rc.getRemainingCloudletLength();
						minResource = tc.getAllocatedReousrce();
					}
				}
			}
			
			double remainTime = minResource == 0 ? 0 : minRemainLength/minResource;
			
			double availResource = mipsShare.get(0) - minUse;
			if((rcl.getCloudletLength() / (availResource)) <= (rcl.getCloudletLength()/(availResource + minResource) + (remainTime))) {
				getCloudletExecList().add(rcl);
				rcl.setCloudletStatus(Cloudlet.INEXEC);
				task.setAllocationResource(mipsShare.get(0) - minUse);
			}else {
				rcl.setCloudletStatus(Cloudlet.QUEUED);
				getCloudletWaitingList().add(rcl);
				return 0.0;
			}
		} else {
			double minUse = mipsShare.get(0);
			int index = 0;
			for(int i = 0; i < taskMap.size(); i++) {
				double usageMips = 0;
				for(ResCloudlet rc : taskMap.get(i)) {
					Task_Custom tc = (Task_Custom)rc.getCloudlet();
					usageMips += tc.getAllocatedReousrce();
				}
				if(minUse > usageMips) {
					minUse = usageMips;
					index = i;
				}
			}
			
			if(mipsShare.get(index) - minUse >= task.getAllocatedReousrce()) {
				getCloudletExecList().add(rcl);
				rcl.setCloudletStatus(Cloudlet.INEXEC);
				taskMap.get(index).add(rcl);
			}else {
				rcl.setCloudletStatus(Cloudlet.QUEUED);
				getCloudletWaitingList().add(rcl);
				return 0.0;
			}
		}

		// use the current capacity to estimate the extra amount of
		// time to file transferring. It must be added to the cloudlet length
		double extraSize = getCapacity(getCurrentMipsShare(), rcl) * fileTransferTime;
		long length = (long) (cloudlet.getCloudletLength() + extraSize);
		cloudlet.setCloudletLength(length);

		return cloudlet.getCloudletLength() / task.getAllocatedReousrce();
	}

	@Override
	public double cloudletSubmit(Cloudlet cloudlet) {
		return cloudletSubmit(cloudlet, 0.0);
	}

	@Override
	public int getCloudletStatus(int cloudletId) {
		for (ResCloudlet rcl : getCloudletExecList()) {
			if (rcl.getCloudletId() == cloudletId) {
				return rcl.getCloudletStatus();
			}
		}
		
		for (ResCloudlet rcl : getCloudletPausedList()) {
			if (rcl.getCloudletId() == cloudletId) {
				return rcl.getCloudletStatus();
			}
		}
		
		for (ResCloudlet rcl : getCloudletWaitingList()) {
			if (rcl.getCloudletId() == cloudletId) {
				return rcl.getCloudletStatus();
			}
		}
		return -1;
	}

	@Override
	public double getTotalUtilizationOfCpu(double time) {
		double totalUtilization = 0;
		for (ResCloudlet gl : getCloudletExecList()) {
			totalUtilization += gl.getCloudlet().getUtilizationOfCpu(time);
		}
		return totalUtilization;
	}

	@Override
	public boolean isFinishedCloudlets() {
		return getCloudletFinishedList().size() > 0;
	}

	@Override
	public Cloudlet getNextFinishedCloudlet() {
		if (getCloudletFinishedList().size() > 0) {
			return getCloudletFinishedList().remove(0).getCloudlet();
		}
		return null;
	}

	@Override
	public int runningCloudlets() {
		return getCloudletExecList().size();
	}

	@Override
	public Cloudlet migrateCloudlet() {
		ResCloudlet rgl = getCloudletExecList().remove(0);
		rgl.finalizeCloudlet();
		return rgl.getCloudlet();
	}

	@Override
	public List<Double> getCurrentRequestedMips() {
		List<Double> mipsShare = new ArrayList<Double>();
		if (getCurrentMipsShare() != null) {
			for (Double mips : getCurrentMipsShare()) {
				mipsShare.add(mips);
			}
		}
		return mipsShare;
	}

	@Override
	public double getTotalCurrentAvailableMipsForCloudlet(ResCloudlet rcl, List<Double> mipsShare) {
            /*@todo It isn't being used any the the given parameters.*/
            return getCapacity(getCurrentMipsShare(), rcl);
	}

	@Override
	public double getTotalCurrentAllocatedMipsForCloudlet(ResCloudlet rcl, double time) {
                //@todo The method is not implemented, in fact
		return 0.0;
	}

	@Override
	public double getTotalCurrentRequestedMipsForCloudlet(ResCloudlet rcl, double time) {
                //@todo The method is not implemented, in fact
		// TODO Auto-generated method stub
		return 0.0;
	}

	@Override
	public double getCurrentRequestedUtilizationOfRam() {
		double ram = 0;
		for (ResCloudlet cloudlet : cloudletExecList) {
			ram += cloudlet.getCloudlet().getUtilizationOfRam(CloudSim.clock());
		}
		return ram;
	}

	@Override
	public double getCurrentRequestedUtilizationOfBw() {
		double bw = 0;
		for (ResCloudlet cloudlet : cloudletExecList) {
			bw += cloudlet.getCloudlet().getUtilizationOfBw(CloudSim.clock());
		}
		return bw;
	}
}