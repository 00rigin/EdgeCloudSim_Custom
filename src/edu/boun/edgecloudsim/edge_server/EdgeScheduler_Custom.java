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

//import edu.boun.edgecloudsim.edge_server.CloudletScheduler_Custom;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.edge_client.Task_Custom;





// 20211021 HJ 멀티레벨큐를 하면서 전체 다 수정됨.
//20211023 위에꺼 취소하고 새롭게 짜기 시작
/*
 * 여기에 웨이팅 큐 3개 만들어서 해보려고 함
 * Task_Custom tc = (Task_Custom)rcl.getCloudlet();
 * tc.setRemainTime(1, estimatedFinishTime - currentTime);
 * 
 * */
public class EdgeScheduler_Custom extends CloudletScheduler {
//public class EdgeScheduler_Custom extends CloudletScheduler_Custom {
	
	/** The number of PEs currently available for the VM using the scheduler,
     * according to the mips share provided to it by
     * {@link #updateVmProcessing(double, java.util.List)} method. */
	protected int currentCPUs;
	protected double remainTime[] = {0,0,0};
	
	
	
	// 20211023 HJ for multilevelQ
	private  List<List<ResCloudlet>> waitingList;
	
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
		waitingList = new ArrayList<>();
		for(int i = 0; i<3; i++) {
			List<ResCloudlet> priorityQ = new ArrayList<ResCloudlet>();
			waitingList.add(priorityQ);
		}
		
	
	}
	
	public List<List<ResCloudlet>> getWaitingList(){
		return waitingList;
	}

	// 각 클래스 큐에서 남은 시간 
	public double getRemainTime(int _class_) {
		double res = 0;
		for(int i = 0; i<_class_; i++) {
			res+=remainTime[i];
		}
		return res;
		
	}
	
	@Override
	public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
		setCurrentMipsShare(mipsShare);
		double timeSpam = currentTime - getPreviousTime();
		
		
		for (ResCloudlet rcl : getCloudletExecList()) {
			rcl.updateCloudletFinishedSoFar((long) (getCapacity(mipsShare) * timeSpam * rcl.getNumberOfPes() * Consts.MILLION));
		}
	
//		if (getCloudletExecList().size() == 0) {
//			setPreviousTime(currentTime);
//			System.out.println("IN");
//			return 0.0;
//		}

		// check finished cloudlets
		double nextEvent = Double.MAX_VALUE;
		List<ResCloudlet> toRemove = new ArrayList<ResCloudlet>();
		for (ResCloudlet rcl : getCloudletExecList()) {
			long remainingLength = rcl.getRemainingCloudletLength();
			if (remainingLength == 0) {// finished: remove from the list
				toRemove.add(rcl);
				cloudletFinish(rcl);
				continue;
			}
		}
		
		if (!waitingList.get(0).isEmpty()) { // waiting list에 작업 들어있으면 exet에 넣어줌
			
			// waiting list에서 상위 큐에있는 작업부터 exec 리스트에 삽압

			for(int k = 0; k<waitingList.get(0).size(); k++) {
				ResCloudlet rcl = waitingList.get(0).get(k);
				rcl.setCloudletStatus(Cloudlet.INEXEC);
				getCloudletExecList().add(rcl);
				waitingList.get(0).remove(rcl);
				
				
				double reMain = (rcl.getRemainingCloudletLength() / (getCapacity(mipsShare) * rcl.getNumberOfPes()));
				Task_Custom tc = (Task_Custom)rcl.getCloudlet();
				// 큐잉타임 업데이트
				remainTime[0] -= reMain;
			}
			
		}
			
		if(!waitingList.get(1).isEmpty()) {
			if(!waitingList.get(0).isEmpty()) { // 1클래스 웨이트 큐 비면
				for(int k = 0; k<waitingList.get(1).size(); k++) {
					ResCloudlet rcl = waitingList.get(1).get(k);
					rcl.setCloudletStatus(Cloudlet.INEXEC);
					getCloudletExecList().add(rcl);
					waitingList.get(1).remove(rcl);
					double reMain = (rcl.getRemainingCloudletLength() / (getCapacity(mipsShare) * rcl.getNumberOfPes()));
					// 큐잉타임 업데이트
					remainTime[1] -= reMain;
				}
				
			}
			// 하위 큐 대기가 대드라인 넘을것으로 예상되면 향상시켜줌
			for(int k = 0; k<waitingList.get(1).size(); k++) {
				ResCloudlet rcl = waitingList.get(1).get(k);
				Task_Custom tc = (Task_Custom)rcl.getCloudlet();
				double reMain = (rcl.getRemainingCloudletLength() / (getCapacity(mipsShare) * rcl.getNumberOfPes()));
				if(tc.getTaskDeadline() < getRemainTime(1)) {
					waitingList.get(0).add(rcl);
					waitingList.get(1).remove(rcl);
				}
				remainTime[0] += reMain;
				remainTime[1] -= reMain;
				
			}

		}
		if(!waitingList.get(2).isEmpty()) {
			if(!waitingList.get(0).isEmpty() && !waitingList.get(1).isEmpty()) { // 1클래스 웨이트 큐 비면
				for(int k = 0; k<waitingList.get(2).size(); k++) {
					ResCloudlet rcl = waitingList.get(2).get(k);
					rcl.setCloudletStatus(Cloudlet.INEXEC);
					getCloudletExecList().add(rcl);
					waitingList.get(2).remove(rcl);
					double reMain = (rcl.getRemainingCloudletLength() / (getCapacity(mipsShare) * rcl.getNumberOfPes()));
					// 큐잉타임 업데이트
					remainTime[2] -= reMain;
				}
			}
			// 하위 큐 대기가 대드라인 넘을것으로 예상되면 향상시켜줌
			for(int k = 0; k<waitingList.get(2).size(); k++) {
				ResCloudlet rcl = waitingList.get(2).get(k);
				Task_Custom tc = (Task_Custom)rcl.getCloudlet();
				double reMain = (rcl.getRemainingCloudletLength() / (getCapacity(mipsShare) * rcl.getNumberOfPes()));
				if(tc.getTaskDeadline() < getRemainTime(2)) {
					waitingList.get(1).add(rcl);
					waitingList.get(2).remove(rcl);
				}
				remainTime[1] += reMain;
				remainTime[2] -= reMain;
			}				
		}
		
		
		
		getCloudletExecList().removeAll(toRemove);
		
		
		if (getCloudletExecList().size() == 0) {
			setPreviousTime(currentTime);
//			System.out.println("IN");
			return 0.0;
		}
	
		// estimate finish time of cloudlets
		for (ResCloudlet rcl : getCloudletExecList()) {
			Task_Custom tc = (Task_Custom)rcl.getCloudlet();
			int pri = tc.getTaskPriority();
			
			double estimatedFinishTime = currentTime
					+ (rcl.getRemainingCloudletLength() / (getCapacity(mipsShare) * rcl.getNumberOfPes()));
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
	protected double getCapacity(List<Double> mipsShare) {
		double capacity = 0.0;
		int cpus = 0;
		for (Double mips : mipsShare) {
			capacity += mips;
			if (mips > 0.0) {
				cpus++;
			}
		}
		currentCPUs = cpus;
	
		int pesInUse = 0;
		for (ResCloudlet rcl : getCloudletExecList()) {
			pesInUse += rcl.getNumberOfPes();
			
		}
	
		if (pesInUse > currentCPUs) {
			capacity /= pesInUse;
		} else {
			capacity /= currentCPUs;
		}
		return capacity;
	}
	
	@Override
	public Cloudlet cloudletCancel(int cloudletId) {
		boolean found = false;
		int position = 0;
	
		// First, looks in the finished queue
		found = false;
		for (ResCloudlet rcl : getCloudletFinishedList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}
	
		if (found) {
			return getCloudletFinishedList().remove(position).getCloudlet();
		}
	
		// Then searches in the exec list
		position=0;
		for (ResCloudlet rcl : getCloudletExecList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}
	
		if (found) {
			ResCloudlet rcl = getCloudletExecList().remove(position);
			if (rcl.getRemainingCloudletLength() == 0) {
				cloudletFinish(rcl);
			} else {
				rcl.setCloudletStatus(Cloudlet.CANCELED);
			}
			return rcl.getCloudlet();
		}
	
		// Now, looks in the paused queue
		found = false;
		position=0;
		for (ResCloudlet rcl : getCloudletPausedList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				rcl.setCloudletStatus(Cloudlet.CANCELED);
				break;
			}
			position++;
		}
	
		if (found) {
			return getCloudletPausedList().remove(position).getCloudlet();
		}
		
		//20211023 HJ
		// Now, looks in the waiting queue
		for(int k = 0; k<3; k++) {
			for (ResCloudlet rcl : waitingList.get(k)) {
				if(rcl.getCloudletId() == cloudletId) {
					rcl.setCloudletStatus(Cloudlet.CANCELED);
					
					waitingList.get(k).remove(rcl);
					return rcl.getCloudlet();
				}
			}
		}
		
		
	
		return null;
	}
	
	@Override
	public boolean cloudletPause(int cloudletId) {
		boolean found = false;
		int position = 0;
	
		for (ResCloudlet rcl : getCloudletExecList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}
	
		if (found) {
			// remove cloudlet from the exec list and put it in the paused list
			ResCloudlet rcl = getCloudletExecList().remove(position);
			if (rcl.getRemainingCloudletLength() == 0) {
				cloudletFinish(rcl);
			} else {
				rcl.setCloudletStatus(Cloudlet.PAUSED);
				getCloudletPausedList().add(rcl);
			}
			return true;
		}
		
		// now, look for the cloudlet in the waiting list
		position = 0;
		found = false;
		boolean flag = false;
		for(int k = 0; k<3; k++) {
			for (ResCloudlet rcl : waitingList.get(k)) {
				if (rcl.getCloudletId() == cloudletId) {
					found = true;
					flag = true;
					break;
				}
				position++;
			}

			if (found) {
				// moves to the paused list
				ResCloudlet rgl = waitingList.get(k).remove(position);
				
				if (rgl.getRemainingCloudletLength() == 0) {
					cloudletFinish(rgl);
				} else {
					rgl.setCloudletStatus(Cloudlet.PAUSED);
					getCloudletPausedList().add(rgl);
				}
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public void cloudletFinish(ResCloudlet rcl) {
		rcl.setCloudletStatus(Cloudlet.SUCCESS);
		rcl.finalizeCloudlet();
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
			ResCloudlet rgl = getCloudletPausedList().remove(position);
			rgl.setCloudletStatus(Cloudlet.INEXEC);
			getCloudletExecList().add(rgl);
	
			// calculate the expected time for cloudlet completion
			// first: how many PEs do we have?
	
			double remainingLength = rgl.getRemainingCloudletLength();
			double estimatedFinishTime = CloudSim.clock()
					+ (remainingLength / (getCapacity(getCurrentMipsShare()) * rgl.getNumberOfPes()));
	
			return estimatedFinishTime;
		}
	
		return 0.0;
	}
	
	@Override
	public double cloudletSubmit(Cloudlet cloudlet, double fileTransferTime) {
		ResCloudlet rcl = new ResCloudlet(cloudlet);
		
		//List<Double> mipsShare = getCurrentMipsShare();
//		System.out.println(mipsShare);
		
		Task_Custom tc = (Task_Custom)rcl.getCloudlet();
//		System.out.println(tc.getAllocatedReousrce());
//		rcl.setCloudletStatus(Cloudlet.INEXEC);
		for (int i = 0; i < cloudlet.getNumberOfPes(); i++) {
			rcl.setMachineAndPeId(0, i);
		}
		
		// waitnglist에 추가.
		
		
		
		rcl.setCloudletStatus(Cloudlet.QUEUED);
		waitingList.get(tc.getTaskPriority()).add(rcl);

		
		
		
	
//		getCloudletExecList().add(rcl);
//	
//		
//		
//		
//		
//		
//		// use the current capacity to estimate the extra amount of
//		// time to file transferring. It must be added to the cloudlet length
//		double extraSize = getCapacity(getCurrentMipsShare()) * fileTransferTime;
//		long length = (long) (cloudlet.getCloudletLength() + extraSize);
//		cloudlet.setCloudletLength(length);
//	
//		return cloudlet.getCloudletLength() / getCapacity(getCurrentMipsShare());
		return 0.0;
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
		return -1;
	}
	
	@Override
	public double getTotalUtilizationOfCpu(double time) {
	            /*
	             * @todo 
	             */
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
		return mipsShare;
	}
	
	@Override
	public double getTotalCurrentAvailableMipsForCloudlet(ResCloudlet rcl, List<Double> mipsShare) {
	        /*@todo It isn't being used any the the given parameters.*/
	        return getCapacity(getCurrentMipsShare());
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