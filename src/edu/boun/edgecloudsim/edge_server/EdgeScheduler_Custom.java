package edu.boun.edgecloudsim.edge_server;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.edge_server.CloudletScheduler_Custom;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.edge_client.Task_Custom;




//public class EdgeScheduler_Custom extends CloudletScheduler {
// 20211021 HJ 멀티레벨큐를 하면서 전체 다 수정됨.
public class EdgeScheduler_Custom extends CloudletScheduler_Custom {
		/** The number of PEs currently available for the VM using the scheduler,
	     * according to the mips share provided to it by
	     * {@link #updateVmProcessing(double, java.util.List)} method. */
	protected int currentCpus;
	
	/** The number of used PEs. */
	protected int usedPes;
	
	/**
	 * Creates a new CloudletSchedulerSpaceShared object. This method must be invoked before
	 * starting the actual simulation.
	 * 
	 * @pre $none
	 * @post $none
	 */
	public EdgeScheduler_Custom() {
		super();
		usedPes = 0;
		currentCpus = 0;
	}
	
	public int getPriority(ResCloudlet rcl) {
		Task_Custom tc = (Task_Custom)rcl.getCloudlet();
		return tc.getTaskPriority();
	}
	
	@Override
	public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
		setCurrentMipsShare(mipsShare);
		double timeSpam = currentTime - getPreviousTime(); // time since last update
		double capacity = 0.0;
		int cpus = 0;
	
		for (Double mips : mipsShare) { // count the CPUs available to the VMM
			capacity += mips;
			if (mips > 0) {
				cpus++;
			}
		}
		currentCpus = cpus;
		capacity /= cpus; // average capacity of each cpu
	
		// each machine in the exec list has the same amount of cpu
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
				
				rcl.updateCloudletFinishedSoFar(
	                                (long) (capacity * timeSpam * rcl.getNumberOfPes() * Consts.MILLION));
			}
		}
		

		// no more cloudlets in this scheduler
		// 모든 큐가 다 비면 종료
		int _sum_ = 0;
		for(int _class_ = 0; _class_<3; _class_++) {
			_sum_ = _sum_ + getCloudletExecList(_class_).size() + getCloudletWaitingList(_class_).size();
		}
		if(_sum_ == 0) {
			setPreviousTime(currentTime);
			return 0.0;
		}
		
		// update each cloudlet
//		int finished = 0;
		int[] finished = {0,0,0}; //high, mid, low
		List[] toRemove = new List[3];
		List<ResCloudlet> toRemoveHigh = new ArrayList<ResCloudlet>();
		List<ResCloudlet> toRemoveMid = new ArrayList<ResCloudlet>();
		List<ResCloudlet> toRemoveLow = new ArrayList<ResCloudlet>();
		toRemove[0] = toRemoveHigh;
		toRemove[1] = toRemoveMid;
		toRemove[2] = toRemoveLow;
		
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
				
				// finished anyway, rounding issue...
				if (rcl.getRemainingCloudletLength() == 0) {
					toRemove[_class_].add(rcl);
					cloudletFinish(rcl);
					finished[_class_]++;
				}
			}
			getCloudletExecList(_class_).removeAll(toRemove[_class_]);
		}
		
	
		// for each finished cloudlet, add a new one from the waiting list
		for(int _class_ = 0; _class_<3; _class_++) {
			if (!getCloudletWaitingList(_class_).isEmpty()) {
				
				for (int i = 0; i < finished[_class_]; i++) {
					
					toRemove[_class_].clear();
					for (ResCloudlet rcl : getCloudletWaitingList(_class_)) {
						
						if ((currentCpus - usedPes) >= rcl.getNumberOfPes()) {
							rcl.setCloudletStatus(Cloudlet.INEXEC);
							for (int k = 0; k < rcl.getNumberOfPes(); k++) {
								rcl.setMachineAndPeId(0, i);
							}
							getCloudletExecList(_class_).add(rcl);
							usedPes += rcl.getNumberOfPes();
							toRemove[_class_].add(rcl);
							break;
						}
					}
					getCloudletWaitingList(_class_).removeAll(toRemove[_class_]);
				}
			}
		}
		
		//Task_Custom tc = (Task_Custom)rcl.getCloudlet();
		
		// estimate finish time of cloudlets in the execution queue
		double nextEvent = Double.MAX_VALUE; // 아마 exe q 끝나는 시간 예측한 값
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
				double remainingLength = rcl.getRemainingCloudletLength();
				double estimatedFinishTime = currentTime + (remainingLength / (capacity * rcl.getNumberOfPes()));
				if (estimatedFinishTime - currentTime < CloudSim.getMinTimeBetweenEvents()) {
					estimatedFinishTime = currentTime + CloudSim.getMinTimeBetweenEvents();
				}
				if (estimatedFinishTime < nextEvent) {
					nextEvent = nextEvent + estimatedFinishTime;
				}
				// 2,3 클래스 태스크가 이 큐에서 끝나기까지 기다려야하는 시간
				if(_class_ == 1) {
					Task_Custom tc = (Task_Custom)rcl.getCloudlet();
					tc.setRemainTime(1, estimatedFinishTime - currentTime);
				}
				else if(_class_ == 2) {
					Task_Custom tc = (Task_Custom)rcl.getCloudlet();
					tc.setRemainTime(2, estimatedFinishTime - currentTime);
				}
			}
		}
		
		setPreviousTime(currentTime);
		return nextEvent;
	}	
	
	@Override
	public Cloudlet cloudletCancel(int cloudletId) {
		
		int _priority_ = 0;
		// First, looks in the finished queue
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletFinishedList(_class_)) {
				if (rcl.getCloudletId() == cloudletId) {
					_priority_ = getPriority(rcl);
					getCloudletFinishedList(_class_).remove(rcl);
					return rcl.getCloudlet();
				}
			}
		}
		
	
		// Then searches in the exec list
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
				if (rcl.getCloudletId() == cloudletId) {
					getCloudletExecList(_class_).remove(rcl);
					if (rcl.getRemainingCloudletLength() == 0) {
						cloudletFinish(rcl);
					} else {
						rcl.setCloudletStatus(Cloudlet.CANCELED);
					}
					return rcl.getCloudlet();
				}
			}
		}
		
	
		// Now, looks in the paused queue
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletPausedList(_class_)) {
				if (rcl.getCloudletId() == cloudletId) {
					getCloudletPausedList(_class_).remove(rcl);
					return rcl.getCloudlet();
				}
			}
		}
		
	
		// Finally, looks in the waiting list
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletWaitingList(_class_)) {
				if (rcl.getCloudletId() == cloudletId) {
					rcl.setCloudletStatus(Cloudlet.CANCELED);
					getCloudletWaitingList(_class_).remove(rcl);
					return rcl.getCloudlet();
				}
			}
		}
		
		return null;
	
	}
	@Override
	public boolean cloudletPause(int cloudletId) {
		boolean[] found = {false,false,false};
//		int position = 0;
		int[] position = {0,0,0};

		// first, looks for the cloudlet in the exec list
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
				if (rcl.getCloudletId() == cloudletId) {
					found[_class_] = true;
					break;
				}
				position[_class_]++;
			}
		}
		

		for(int _class_ = 0; _class_<3; _class_++) {
			if (found[_class_]) {
				// moves to the paused list
				
				ResCloudlet rgl = getCloudletExecList(_class_).remove(position[_class_]);
				if (rgl.getRemainingCloudletLength() == 0) {
					cloudletFinish(rgl);
				} else {
					rgl.setCloudletStatus(Cloudlet.PAUSED);
					getCloudletPausedList(_class_).add(rgl);
				}
				return true;
			}
		}
		

		// now, look for the cloudlet in the waiting list
		for(int i = 0; i<3; i++) {
			position[i] = 0;
			found[i] = false;
		}
			
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletWaitingList(_class_)) {
				if (rcl.getCloudletId() == cloudletId) {
					found[_class_] = true;
					break;
				}
				position[_class_]++;
			}
		}
		
		for(int _class_ = 0; _class_<3; _class_++) {
			if (found[_class_]) {
				// moves to the paused list
				ResCloudlet rgl = getCloudletWaitingList(_class_).remove(position[_class_]);
				if (rgl.getRemainingCloudletLength() == 0) {
					cloudletFinish(rgl);
				} else {
					rgl.setCloudletStatus(Cloudlet.PAUSED);
					getCloudletPausedList(_class_).add(rgl);
				}
				return true;
			}
		}

		return false;
	}
	
	
	@Override
	public void cloudletFinish(ResCloudlet rcl) {
		int priority = getPriority(rcl);
		rcl.setCloudletStatus(Cloudlet.SUCCESS);
		rcl.finalizeCloudlet();
		getCloudletFinishedList(priority).add(rcl);
		usedPes -= rcl.getNumberOfPes();
	}
	
	@Override
	public double cloudletResume(int cloudletId) {

		boolean[] found = {false,false,false};
		int[] position = {0,0,0};
	
		// look for the cloudlet in the paused list
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletPausedList(_class_)) {
				
				if (rcl.getCloudletId() == cloudletId) {
					found[_class_] = true;
					break;
				}
				position[_class_]++;
			}
		
			if (found[_class_]) {
				ResCloudlet rcl = getCloudletPausedList(_class_).remove(position[_class_]);
		
				// it can go to the exec list
				if ((currentCpus - usedPes) >= rcl.getNumberOfPes()) {
					rcl.setCloudletStatus(Cloudlet.INEXEC);
					for (int i = 0; i < rcl.getNumberOfPes(); i++) {
						rcl.setMachineAndPeId(0, i);
					}
		
					long size = rcl.getRemainingCloudletLength();
					size *= rcl.getNumberOfPes();
					rcl.getCloudlet().setCloudletLength(size);
		
					getCloudletExecList(_class_).add(rcl);
					usedPes += rcl.getNumberOfPes();
		
					// calculate the expected time for cloudlet completion
					double capacity = 0.0;
					int cpus = 0;
					for (Double mips : getCurrentMipsShare()) {
						capacity += mips;
						if (mips > 0) {
							cpus++;
						}
					}
					currentCpus = cpus;
					capacity /= cpus;
		
					long remainingLength = rcl.getRemainingCloudletLength();
					double estimatedFinishTime = CloudSim.clock()
							+ (remainingLength / (capacity * rcl.getNumberOfPes()));
		
					return estimatedFinishTime;
				} else {// no enough free PEs: go to the waiting queue
					rcl.setCloudletStatus(Cloudlet.QUEUED);
		
					long size = rcl.getRemainingCloudletLength();
					size *= rcl.getNumberOfPes();
					rcl.getCloudlet().setCloudletLength(size);
		
					getCloudletWaitingList(_class_).add(rcl);
					return 0.0;
				}
		
			}
		}
		// not found in the paused list: either it is in in the queue, executing or not exist
		return 0.0;
	
	}
	
	@Override
	public double cloudletSubmit(Cloudlet cloudlet, double fileTransferTime) {
		// it can go to the exec list
		if ((currentCpus - usedPes) >= cloudlet.getNumberOfPes()) {
			ResCloudlet rcl = new ResCloudlet(cloudlet);
			int priority = getPriority(rcl);
			rcl.setCloudletStatus(Cloudlet.INEXEC);
			
			for (int i = 0; i < cloudlet.getNumberOfPes(); i++) {
				rcl.setMachineAndPeId(0, i);
			}
			getCloudletExecList(priority).add(rcl);
			usedPes += cloudlet.getNumberOfPes();
		} else {// no enough free PEs: go to the waiting queue
			ResCloudlet rcl = new ResCloudlet(cloudlet);
			int priority = getPriority(rcl);
			rcl.setCloudletStatus(Cloudlet.QUEUED);
			getCloudletWaitingList(priority).add(rcl);
			return 0.0;
		}
	
		// calculate the expected time for cloudlet completion
		double capacity = 0.0;
		int cpus = 0;
		for (Double mips : getCurrentMipsShare()) {
			capacity += mips;
			if (mips > 0) {
				cpus++;
			}
		}
	
		currentCpus = cpus;
		capacity /= cpus;
	
		// use the current capacity to estimate the extra amount of
		// time to file transferring. It must be added to the cloudlet length
		double extraSize = capacity * fileTransferTime;
		long length = cloudlet.getCloudletLength();
		length += extraSize;
		cloudlet.setCloudletLength(length);
		return cloudlet.getCloudletLength() / capacity;
	}
	
	@Override
	public double cloudletSubmit(Cloudlet cloudlet) {
		return cloudletSubmit(cloudlet, 0.0);
	}
	
	@Override
	public int getCloudletStatus(int cloudletId) {
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
				if (rcl.getCloudletId() == cloudletId) {
					return rcl.getCloudletStatus();
				}
			}
		}
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletPausedList(_class_)) {
				if (rcl.getCloudletId() == cloudletId) {
					return rcl.getCloudletStatus();
				}
			}
		}
		for(int _class_ = 0; _class_<3; _class_++) {
			for (ResCloudlet rcl : getCloudletWaitingList(_class_)) {
				if (rcl.getCloudletId() == cloudletId) {
					return rcl.getCloudletStatus();
				}
			}
		}

		return -1;
	}
	
	@Override
	public double getTotalUtilizationOfCpu(double time) {
		double totalUtilization = 0;
		for(int _class_=0; _class_<3; _class_++) {
			for (ResCloudlet gl : getCloudletExecList(_class_)) {
				totalUtilization += gl.getCloudlet().getUtilizationOfCpu(time);
			}
		}
		return totalUtilization;
	}
	
	@Override
	public boolean isFinishedCloudlets() {
		for(int _class_=0; _class_<3; _class_++) {
			if(getCloudletFinishedList(_class_).size() > 0)
				return true;
		}
		return false;
//		return getCloudletFinishedList().size() > 0; // 사이즈가 0보다 크면 true 리턴
	}
	
	@Override
	public Cloudlet getNextFinishedCloudlet() {
		for(int _class_ = 0; _class_<3; _class_++) {
			if (getCloudletFinishedList(_class_).size() > 0) {
				return getCloudletFinishedList(_class_).remove(0).getCloudlet();
			}
		}
		return null;
	}
	
	@Override
	public int runningCloudlets() {
		int _sum_ = 0;
		for(int _class_ = 0; _class_<3; _class_ ++) 
			_sum_+=getCloudletExecList(_class_).size();

		return _sum_;
//		return getCloudletExecList().size();
	}
	
	/**
	 * Returns the first cloudlet to migrate to another VM.
	 * 
	 * @return the first running cloudlet
	 * @pre $none
	 * @post $none
	     * 
	     * @todo it doesn't check if the list is empty
	 */
	@Override
	public Cloudlet migrateCloudlet() {
		
		ResCloudlet rcl = getCloudletExecList(0).remove(0);
		rcl.finalizeCloudlet();
		Cloudlet cl = rcl.getCloudlet();
		usedPes -= cl.getNumberOfPes();
		return cl;
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
	            /*@todo The param rcl is not being used.*/
		double capacity = 0.0;
		int cpus = 0;
		for (Double mips : mipsShare) { // count the cpus available to the vmm
			capacity += mips;
			if (mips > 0) {
				cpus++;
			}
		}
		currentCpus = cpus;
		capacity /= cpus; // average capacity of each cpu
		return capacity;
	}


	@Override
	public double getTotalCurrentAllocatedMipsForCloudlet(ResCloudlet rcl, double time) {   
                //@todo the method isn't in fact implemented
		// TODO Auto-generated method stub
		return 0.0;
	}

	@Override
	public double getTotalCurrentRequestedMipsForCloudlet(ResCloudlet rcl, double time) {
                //@todo the method isn't in fact implemented
		// TODO Auto-generated method stub
		return 0.0;
	}

	@Override
	public double getCurrentRequestedUtilizationOfRam() {
                //@todo the method isn't in fact implemented
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public double getCurrentRequestedUtilizationOfBw() {
                //@todo the method isn't in fact implemented
		// TODO Auto-generated method stub
		return 0;
	}
	
	
//	protected int currentCpus;
//
//	/** The number of used PEs. */
//	protected int usedPes;
//	
//	
//	
//
//	/**
//	 * Creates a new CloudletSchedulerSpaceShared object. This method must be invoked before
//	 * starting the actual simulation.
//	 * 
//	 * @pre $none
//	 * @post $none
//	 */
//	public EdgeScheduler_Custom() {
//		super();
//		usedPes = 0;
//		currentCpus = 0;
//	}
//	
//	
//
//	@Override
//	public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
//		setCurrentMipsShare(mipsShare);
//		double timeSpam = currentTime - getPreviousTime(); // time since last update
//		double capacity = 0.0;
//		int cpus = 0;
//
//		for (Double mips : mipsShare) { // count the CPUs available to the VMM
//			capacity += mips;
//			if (mips > 0) {
//				cpus++;
//			}
//		}
//		currentCpus = cpus;
//		capacity /= cpus; // average capacity of each cpu
//
//		// each machine in the exec list has the same amount of cpu
//		for(int _class_ = 0; _class_<3; _class_++) {
//			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
//				
//				rcl.updateCloudletFinishedSoFar(
//	                                (long) (capacity * timeSpam * rcl.getNumberOfPes() * Consts.MILLION));
//			}
//		}
//		
//
//		// no more cloudlets in this scheduler
//		// 모든 큐가 다 비면 종료
//		int _sum_ = 0;
//		for(int _class_ = 0; _class_<3; _class_++) {
//			_sum_ = _sum_ + getCloudletExecList(_class_).size() + getCloudletWaitingList(_class_).size();
//		}
//		if(_sum_ == 0) {
//			setPreviousTime(currentTime);
//			return 0.0;
//		}
//		
//
//		// update each cloudlet
////		int finished = 0;
//		int[] finished = {0,0,0}; //high, mid, low
//		List[] toRemove = new List[3];
//		List<ResCloudlet> toRemoveHigh = new ArrayList<ResCloudlet>();
//		List<ResCloudlet> toRemoveMid = new ArrayList<ResCloudlet>();
//		List<ResCloudlet> toRemoveLow = new ArrayList<ResCloudlet>();
//		toRemove[0] = toRemoveHigh;
//		toRemove[1] = toRemoveMid;
//		toRemove[2] = toRemoveLow;
//		
//		for(int _class_ = 0; _class_<3; _class_++) {
//			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
//				
//				// finished anyway, rounding issue...
//				if (rcl.getRemainingCloudletLength() == 0) {
//					toRemove[_class_].add(rcl);
//					cloudletFinish(rcl);
//					finished[_class_]++;
//				}
//			}
//			getCloudletExecList(_class_).removeAll(toRemove[_class_]);
//		}
//		
//
//		// for each finished cloudlet, add a new one from the waiting list
//		for(int _class_ = 0; _class_<3; _class_++) {
//			if (!getCloudletWaitingList(_class_).isEmpty()) {
//				
//				for (int i = 0; i < finished[_class_]; i++) {
//					
//					toRemove[_class_].clear();
//					for (ResCloudlet rcl : getCloudletWaitingList(_class_)) {
//						
//						if ((currentCpus - usedPes) >= rcl.getNumberOfPes()) {
//							rcl.setCloudletStatus(Cloudlet.INEXEC);
//							for (int k = 0; k < rcl.getNumberOfPes(); k++) {
//								rcl.setMachineAndPeId(0, i);
//							}
//							getCloudletExecList(_class_).add(rcl);
//							usedPes += rcl.getNumberOfPes();
//							toRemove[_class_].add(rcl);
//							break;
//						}
//					}
//					getCloudletWaitingList(_class_).removeAll(toRemove[_class_]);
//				}
//			}
//		}
//		
//
//		// estimate finish time of cloudlets in the execution queue
//		double nextEvent = Double.MAX_VALUE; // 아마 exe q 끝나는 시간 예측한 값
//		for(int _class_ = 0; _class_<3; _class_++) {
//			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
//				double remainingLength = rcl.getRemainingCloudletLength();
//				double estimatedFinishTime = currentTime + (remainingLength / (capacity * rcl.getNumberOfPes()));
//				if (estimatedFinishTime - currentTime < CloudSim.getMinTimeBetweenEvents()) {
//					estimatedFinishTime = currentTime + CloudSim.getMinTimeBetweenEvents();
//				}
//				if (estimatedFinishTime < nextEvent) {
//					nextEvent = nextEvent + estimatedFinishTime;
//				}
//			}
//		}
//		
//		setPreviousTime(currentTime);
//		return nextEvent;
//	}
//
//	@Override
//	// priority를 다루기 위해 for를 써도 되는가? -> cloudletID는 어차피 하나이기 때문에 이중for문 돌아도 문제가 없을것! 
//	// cloudlet에 들어있는 태스크의 priority를 어케 찾지?
//	public Cloudlet cloudletCancel(int cloudletId) {
//		// First, looks in the finished queue
//		for(int _class_ = 0; _class_<3; _class_++) {
//			for (ResCloudlet rcl : getCloudletFinishedList(_class_)) {
//				if (rcl.getCloudletId() == cloudletId) {
//					getCloudletFinishedList(_class_).remove(rcl);
//					return rcl.getCloudlet();
//				}
//			}
//		}
//		
//
//		// Then searches in the exec list
//		for(int _class_ = 0; _class_<3; _class_++) {
//			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
//				if (rcl.getCloudletId() == cloudletId) {
//					getCloudletExecList(_class_).remove(rcl);
//					if (rcl.getRemainingCloudletLength() == 0) {
//						cloudletFinish(rcl);
//					} else {
//						rcl.setCloudletStatus(Cloudlet.CANCELED);
//					}
//					return rcl.getCloudlet();
//				}
//			}
//		}
//		
//
//		// Now, looks in the paused queue
//		for(int _class_ = 0; _class_<3; _class_++) {
//			for (ResCloudlet rcl : getCloudletPausedList(_class_)) {
//				if (rcl.getCloudletId() == cloudletId) {
//					getCloudletPausedList(_class_).remove(rcl);
//					return rcl.getCloudlet();
//				}
//			}
//		}
//		
//
//		// Finally, looks in the waiting list
//		for(int _class_ = 0; _class_<3; _class_++) {
//			for (ResCloudlet rcl : getCloudletWaitingList(_class_)) {
//				if (rcl.getCloudletId() == cloudletId) {
//					rcl.setCloudletStatus(Cloudlet.CANCELED);
//					getCloudletWaitingList(_class_).remove(rcl);
//					return rcl.getCloudlet();
//				}
//			}
//		}
//		
//
//		return null;
//
//	}
//
//	@Override
//	public boolean cloudletPause(int cloudletId) {
//		boolean[] found = {false,false,false};
////		int position = 0;
//		int[] position = {0,0,0};
//
//		// first, looks for the cloudlet in the exec list
//		for(int _class_ = 0; _class_<3; _class_++) {
//			for (ResCloudlet rcl : getCloudletExecList(_class_)) {
//				if (rcl.getCloudletId() == cloudletId) {
//					found[_class_] = true;
//					break;
//				}
//				position[_class_]++;
//			}
//		}
//		
//
//		for(int _class_ = 0; _class_<3; _class_++) {
//			if (found[_class_]) {
//				// moves to the paused list
//				
//				ResCloudlet rgl = getCloudletExecList(_class_).remove(position[_class_]);
//				if (rgl.getRemainingCloudletLength() == 0) {
//					cloudletFinish(rgl);
//				} else {
//					rgl.setCloudletStatus(Cloudlet.PAUSED);
//					getCloudletPausedList(_class_).add(rgl);
//				}
//				return true;
//			}
//		}
//		
//
//		// now, look for the cloudlet in the waiting list
//		for(int i = 0; i<3; i++) {
//			position[i] = 0;
//			found[i] = false;
//		}
//			
//		for(int _class_ = 0; _class_<3; _class_++) {
//			for (ResCloudlet rcl : getCloudletWaitingList(_class_)) {
//				if (rcl.getCloudletId() == cloudletId) {
//					found[_class_] = true;
//					break;
//				}
//				position[_class_]++;
//			}
//		}
//		
//		for(int _class_ = 0; _class_<3; _class_++) {
//			if (found[_class_]) {
//				// moves to the paused list
//				ResCloudlet rgl = getCloudletWaitingList(_class_).remove(position[_class_]);
//				if (rgl.getRemainingCloudletLength() == 0) {
//					cloudletFinish(rgl);
//				} else {
//					rgl.setCloudletStatus(Cloudlet.PAUSED);
//					getCloudletPausedList(_class_).add(rgl);
//				}
//				return true;
//			}
//		}
//
//		return false;
//	}
//	
//	
//	
//	public int findClass(ResCloudlet rcl) {
//		int _class_ = 0;
//		
//		
//		return _class_;
//	}
//
//	@Override
//	public void cloudletFinish(ResCloudlet rcl) {
//		rcl.
//		rcl.setCloudletStatus(Cloudlet.SUCCESS);
//		rcl.finalizeCloudlet();
//		getCloudletFinishedList().add(rcl);
//		usedPes -= rcl.getNumberOfPes();
//	}
//
//	@Override
//	public double cloudletResume(int cloudletId) {
//		boolean found = false;
//		int position = 0;
//
//		// look for the cloudlet in the paused list
//		for (ResCloudlet rcl : getCloudletPausedList()) {
//			if (rcl.getCloudletId() == cloudletId) {
//				found = true;
//				break;
//			}
//			position++;
//		}
//
//		if (found) {
//			ResCloudlet rcl = getCloudletPausedList().remove(position);
//
//			// it can go to the exec list
//			if ((currentCpus - usedPes) >= rcl.getNumberOfPes()) {
//				rcl.setCloudletStatus(Cloudlet.INEXEC);
//				for (int i = 0; i < rcl.getNumberOfPes(); i++) {
//					rcl.setMachineAndPeId(0, i);
//				}
//
//				long size = rcl.getRemainingCloudletLength();
//				size *= rcl.getNumberOfPes();
//				rcl.getCloudlet().setCloudletLength(size);
//
//				getCloudletExecList().add(rcl);
//				usedPes += rcl.getNumberOfPes();
//
//				// calculate the expected time for cloudlet completion
//				double capacity = 0.0;
//				int cpus = 0;
//				for (Double mips : getCurrentMipsShare()) {
//					capacity += mips;
//					if (mips > 0) {
//						cpus++;
//					}
//				}
//				currentCpus = cpus;
//				capacity /= cpus;
//
//				long remainingLength = rcl.getRemainingCloudletLength();
//				double estimatedFinishTime = CloudSim.clock()
//						+ (remainingLength / (capacity * rcl.getNumberOfPes()));
//
//				return estimatedFinishTime;
//			} else {// no enough free PEs: go to the waiting queue
//				rcl.setCloudletStatus(Cloudlet.QUEUED);
//
//				long size = rcl.getRemainingCloudletLength();
//				size *= rcl.getNumberOfPes();
//				rcl.getCloudlet().setCloudletLength(size);
//
//				getCloudletWaitingList().add(rcl);
//				return 0.0;
//			}
//
//		}
//
//		// not found in the paused list: either it is in in the queue, executing or not exist
//		return 0.0;
//
//	}
//
//	@Override
//	public double cloudletSubmit(Cloudlet cloudlet, double fileTransferTime) {
//		// it can go to the exec list
//		if ((currentCpus - usedPes) >= cloudlet.getNumberOfPes()) {
//			ResCloudlet rcl = new ResCloudlet(cloudlet);
//			rcl.setCloudletStatus(Cloudlet.INEXEC);
//			for (int i = 0; i < cloudlet.getNumberOfPes(); i++) {
//				rcl.setMachineAndPeId(0, i);
//			}
//			getCloudletExecList().add(rcl);
//			usedPes += cloudlet.getNumberOfPes();
//		} else {// no enough free PEs: go to the waiting queue
//			ResCloudlet rcl = new ResCloudlet(cloudlet);
//			rcl.setCloudletStatus(Cloudlet.QUEUED);
//			getCloudletWaitingList().add(rcl);
//			return 0.0;
//		}
//
//		// calculate the expected time for cloudlet completion
//		double capacity = 0.0;
//		int cpus = 0;
//		for (Double mips : getCurrentMipsShare()) {
//			capacity += mips;
//			if (mips > 0) {
//				cpus++;
//			}
//		}
//
//		currentCpus = cpus;
//		capacity /= cpus;
//
//		// use the current capacity to estimate the extra amount of
//		// time to file transferring. It must be added to the cloudlet length
//		double extraSize = capacity * fileTransferTime;
//		long length = cloudlet.getCloudletLength();
//		length += extraSize;
//		cloudlet.setCloudletLength(length);
//		return cloudlet.getCloudletLength() / capacity;
//	}
//
//	@Override
//	public double cloudletSubmit(Cloudlet cloudlet) {
//		return cloudletSubmit(cloudlet, 0.0);
//	}
//
//	@Override
//	public int getCloudletStatus(int cloudletId) {
//		for (ResCloudlet rcl : getCloudletExecList()) {
//			if (rcl.getCloudletId() == cloudletId) {
//				return rcl.getCloudletStatus();
//			}
//		}
//
//		for (ResCloudlet rcl : getCloudletPausedList()) {
//			if (rcl.getCloudletId() == cloudletId) {
//				return rcl.getCloudletStatus();
//			}
//		}
//
//		for (ResCloudlet rcl : getCloudletWaitingList()) {
//			if (rcl.getCloudletId() == cloudletId) {
//				return rcl.getCloudletStatus();
//			}
//		}
//
//		return -1;
//	}
//
//	@Override
//	public double getTotalUtilizationOfCpu(double time) {
//		double totalUtilization = 0;
//		for (ResCloudlet gl : getCloudletExecList()) {
//			totalUtilization += gl.getCloudlet().getUtilizationOfCpu(time);
//		}
//		return totalUtilization;
//	}
//
//	@Override
//	public boolean isFinishedCloudlets() {
//		return getCloudletFinishedList().size() > 0;
//	}
//
//	@Override
//	public Cloudlet getNextFinishedCloudlet() {
//		if (getCloudletFinishedList().size() > 0) {
//			return getCloudletFinishedList().remove(0).getCloudlet();
//		}
//		return null;
//	}
//
//	@Override
//	public int runningCloudlets() {
//		return getCloudletExecList().size();
//	}
//
//	/**
//	 * Returns the first cloudlet to migrate to another VM.
//	 * 
//	 * @return the first running cloudlet
//	 * @pre $none
//	 * @post $none
//         * 
//         * @todo it doesn't check if the list is empty
//	 */
//	@Override
//	public Cloudlet migrateCloudlet() {
//		ResCloudlet rcl = getCloudletExecList().remove(0);
//		rcl.finalizeCloudlet();
//		Cloudlet cl = rcl.getCloudlet();
//		usedPes -= cl.getNumberOfPes();
//		return cl;
//	}
//
//	@Override
//	public List<Double> getCurrentRequestedMips() {
//		List<Double> mipsShare = new ArrayList<Double>();
//		if (getCurrentMipsShare() != null) {
//			for (Double mips : getCurrentMipsShare()) {
//				mipsShare.add(mips);
//			}
//		}
//		return mipsShare;
//	}
//
//	@Override
//	public double getTotalCurrentAvailableMipsForCloudlet(ResCloudlet rcl, List<Double> mipsShare) {
//                /*@todo The param rcl is not being used.*/
//		double capacity = 0.0;
//		int cpus = 0;
//		for (Double mips : mipsShare) { // count the cpus available to the vmm
//			capacity += mips;
//			if (mips > 0) {
//				cpus++;
//			}
//		}
//		currentCpus = cpus;
//		capacity /= cpus; // average capacity of each cpu
//		return capacity;
//	}
//
//	@Override
//	public double getTotalCurrentAllocatedMipsForCloudlet(ResCloudlet rcl, double time) {   
//                //@todo the method isn't in fact implemented
//		// TODO Auto-generated method stub
//		return 0.0;
//	}
//
//	@Override
//	public double getTotalCurrentRequestedMipsForCloudlet(ResCloudlet rcl, double time) {
//                //@todo the method isn't in fact implemented
//		// TODO Auto-generated method stub
//		return 0.0;
//	}
//
//	@Override
//	public double getCurrentRequestedUtilizationOfRam() {
//                //@todo the method isn't in fact implemented
//		// TODO Auto-generated method stub
//		return 0;
//	}
//
//	@Override
//	public double getCurrentRequestedUtilizationOfBw() {
//                //@todo the method isn't in fact implemented
//		// TODO Auto-generated method stub
//		return 0;
//	}
	

////////////////////////////////////////////////////////////////////////////////	
	
	

}