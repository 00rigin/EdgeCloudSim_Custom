package edu.boun.edgecloudsim.application.jcci;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_client.Task_Custom;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class CustomEdgeOrchestrator extends EdgeOrchestrator{
	
	private int numberOfHost;
	private double varThresh;
	private int startFlag;
	
	
	public CustomEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		// TODO Auto-generated method stub
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
		varThresh = 10;
		startFlag = 1;
	}

	@Override
	public int getDeviceToOffload(Task_Custom task) { 
		// TODO Auto-generated method stub
		int result = 0;
		//dummy task to simulate a task with 1 Mbit file size to upload and download 
//		Task dummyTask = new Task(0, 0, 0, 0, 0, 0, new UtilizationModelFull(),
//				new UtilizationModelFull(), new UtilizationModelFull());
		
		Location sourcePointLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
		double edgeUtilization = SimManager.getInstance().getEdgeServerManager().getEdgeUtilization(sourcePointLocation.getServingWlanId());
		
		// 20211013 HJ for KSC
		if(policy.equals("PROPOSED")) {
			
			
//			else if(policy.equals("RANDOM")) {
//				
//				List<EdgeVM> vmList = SimManager.getInstance().getEdgeServerManager().getVmList(sourcePointLocation.getServingWlanId());
//				task.setAllocationResource(vmList.get(0).getMips());
//				result = SimUtils.getRandomNumber(0, 9);
//				//SimLogger.printLine("Random Edge ID :"+ result);
//			}
//			
//			SimLogger.printLine("Task : " + task.getMobileDeviceId());
			
			
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(sourcePointLocation.getServingWlanId()); 
			List<ResCloudlet> exeList= vmArray.get(0).getCloudletScheduler().getCloudletExecList(); 
			List<Double> mipsShare = vmArray.get(0).getCloudletScheduler().getCurrentMipsShare();
			
			NetworkModel networkModel = SimManager.getInstance().getNetworkModel();	
			
			task.setAllocationResource(vmArray.get(0).getMips());
			
//			SimLogger.printLine("Task Size : " + task.getTaskSize());
//			SimLogger.printLine("Task Deadline : " + task.getTaskDeadline());
//			SimLogger.printLine("Task Throughput : " + (double)task.getTaskSize()/(double)task.getTaskDeadline());
			
//			SimLogger.printLine("CloudSim.clock() : " + CloudSim.clock());
		
			
//			SimLogger.printLine("VMArray : "+vmArray);
//			SimLogger.printLine("exeArray : "+exeList);
//			SimLogger.printLine("mipsShare : "+mipsShare);
//			
//			for (int i = 0; i<vmArray.size(); i++) {
//				SimLogger.printLine("vmArray data :  : "+vmArray.get(0));
//			}
			
			
			
			
			
			
			
			
			
		}
		

		// 20210301 for kcc
		else if(policy.equals("CONV") || policy.equals("PROPOSED_WO_RA")) {
			//resource prediction
			//deadline : 500ms
			double requiredResource = task.getCloudletLength()/0.5;
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(sourcePointLocation.getServingWlanId());
			
			List<ResCloudlet> exeList= vmArray.get(0).getCloudletScheduler().getCloudletExecList();
			List<Double> mipsShare = vmArray.get(0).getCloudletScheduler().getCurrentMipsShare();
			double usage = 0;
			double mips = 0;
			for(ResCloudlet rc : exeList) { ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
				Task_Custom tc = (Task_Custom)rc.getCloudlet();
				usage += tc.getAllocatedReousrce();
			}
			for(double m : mipsShare) {
				mips += m;
			}
			double utilizationByTask = ((requiredResource+usage)/(mips))*100;
			
			if(utilizationByTask < 70) {
				//offloading
				task.setAllocationResource(requiredResource); // (requiredResource) 
				result = sourcePointLocation.getServingWlanId(); // ES ID
			}else {
				double randomValue = SimUtils.getRandomDoubleNumber(0.0, 1.0);
				List<ResCloudlet> execList = vmArray.get(0).getCloudletScheduler().getCloudletExecList();
				List<ResCloudlet> waitingList = vmArray.get(0).getCloudletScheduler().getCloudletWaitingList();
				List<ResCloudlet> finishList = vmArray.get(0).getCloudletScheduler().getCloudletFinishedList();
				long minRemainingLength = Long.MAX_VALUE;
				
				for(ResCloudlet rc : execList) {
					if(rc.getRemainingCloudletLength() < minRemainingLength) {
						minRemainingLength = rc.getRemainingCloudletLength(); ///////////////////////////////////////////////////////////////////
					}
				}
				
				double timeValue = minRemainingLength/0.5; // deadline : 500ms
				double offloadingProb = Math.cbrt((utilizationByTask/mips)*timeValue/100);
				
				if(randomValue < offloadingProb) {
					task.setAllocationResource(requiredResource);
					result = sourcePointLocation.getServingWlanId();
				}else {
					
					ArrayList<Integer> edge = new ArrayList<>(Arrays.asList(0,1,2,3,4,5,6,7,8,9));
					int collaborationTarget = Integer.MAX_VALUE;
					double localDelay = 0;
					double resource = 0;
					
					if(waitingList.size() > 0) {
						double avgResource = usage/execList.size();
						
						localDelay += task.getCloudletLength() / avgResource;
						// queueing delay
						if(waitingList.size() > execList.size()) {
							for(int i = 0; i < execList.size(); i++) {
								Task_Custom t = (Task_Custom)execList.get(i).getCloudlet();
								localDelay += (execList.get(i).getRemainingCloudletLength() / t.getAllocatedReousrce());
							}
							
							for(int i = 0; i < waitingList.size(); i++) {
								Task_Custom t = (Task_Custom)waitingList.get(i).getCloudlet();
								localDelay += (waitingList.get(i).getRemainingCloudletLength() / t.getAllocatedReousrce());
							}
						}else {
							for(int k = 0; k < waitingList.size(); k++) {
								Task_Custom t = (Task_Custom)execList.get(k).getCloudlet();
								localDelay += (execList.get(k).getRemainingCloudletLength() / t.getAllocatedReousrce());
							}
						}
					}else {
						localDelay += (task.getCloudletLength() / (vmArray.get(0).getMips()-usage));
					}
					
					edge = sortingByUtilization(edge);
					
					for(int i = 0; i < edge.size(); i++) {
						List<EdgeVM> edgeVmList = SimManager.getInstance().getEdgeServerManager().getVmList(i);
						List<Double> eMips = edgeVmList.get(0).getCloudletScheduler().getCurrentMipsShare();
						List<ResCloudlet> execList_e = edgeVmList.get(0).getCloudletScheduler().getCloudletExecList();
						List<ResCloudlet> waitingList_e = edgeVmList.get(0).getCloudletScheduler().getCloudletWaitingList();
//						List<ResCloudlet> finishList_e = edgeVmList.get(0).getCloudletScheduler().getCloudletFinishedList();
						
						double mipsUs = 0;
						for(double d : eMips) {
							mipsUs += d;
						}
						// compute delay
						double communicationDelay = 0;
						double bufferDelay = 0;
						double computationDelay = 0;
						
						//
						double avgResource = mipsUs/execList_e.size();
						
						// communication delay
						if(task.getMobileDeviceId() != i) {
							communicationDelay = (task.getCloudletLength() * 8) / 900000000; // 90Mbps
						}
						
						// computation delay
						if(waitingList.size() > 0) {
							computationDelay = task.getCloudletLength() / avgResource;
							resource = avgResource;
						}else {
							computationDelay = task.getCloudletLength() / (edgeVmList.get(0).getMips() - mipsUs);
							resource = edgeVmList.get(0).getMips() - mipsUs;
						}
						
						// buffer delay
						if(edgeVmList.get(0).getMips() - mipsUs < task.getCloudletLength() / 2) {
							if(waitingList_e.size() > execList_e.size()) {
								for(int j = 0; j < execList_e.size(); j++) {
									bufferDelay += (execList_e.get(j).getRemainingCloudletLength() / eMips.get(0));
								}
								for(int j = 0; j < waitingList_e.size(); j++) {
									Task_Custom t = (Task_Custom)waitingList_e.get(j).getCloudlet();
									bufferDelay += (waitingList_e.get(j).getCloudletLength() / t.getAllocatedReousrce());
								}
							}else {
								for(int j = 0; j < waitingList_e.size(); j++) {
									bufferDelay += (execList_e.get(j).getRemainingCloudletLength() / eMips.get(0));
								}
							}
						}
						
						double computationThreshold = (task.getCloudletLength() / (0.5 - communicationDelay - bufferDelay));
						// greedy algorithm
						if(edgeVmList.get(0).getMips() - mipsUs > computationThreshold) {
							task.setAllocationResource(resource);
							collaborationTarget = i;
							return collaborationTarget;
						}else if(avgResource > computationThreshold){
							task.setAllocationResource(resource);
							collaborationTarget = i;
							return collaborationTarget;
						}
					}
					
					edge = sortByBuffer(edge);
					int numTask = (int) ((vmArray.get(0).getMips() * 0.5)/task.getCloudletLength());
					double totalFinishTime = 0;
					double total = 0;
					ArrayList<Double> completionTime = new ArrayList<Double>();
					
					for(int i = finishList.size() - 1; i > numTask; i--) { // 
						completionTime.add(finishList.get(i).getClouddletFinishTime() - finishList.get(i).getCloudletArrivalTime());
						totalFinishTime += finishList.get(i).getClouddletFinishTime() - finishList.get(i).getCloudletArrivalTime();
					}
					
					totalFinishTime = totalFinishTime / finishList.size();
					
					for(int i = 0; i < completionTime.size(); i++) {
						total += Math.pow(completionTime.get(i) - totalFinishTime, 2);
					}
					
					double sDelay = 0.5 * (1 + Math.cbrt(total));
					
					for(int i=0; i < edge.size(); i++) {
						List<EdgeVM> edgeVmList = SimManager.getInstance().getEdgeServerManager().getVmList(i);
						List<Double> eMips = edgeVmList.get(0).getCloudletScheduler().getCurrentMipsShare();
						List<ResCloudlet> execList_e = edgeVmList.get(0).getCloudletScheduler().getCloudletExecList();
						List<ResCloudlet> waitingList_e = edgeVmList.get(0).getCloudletScheduler().getCloudletWaitingList();
						
						double mipsUs = 0;
						for(double d : eMips) {
							mipsUs += d;
						}
						// compute delay
						double communicationDelay = 0;
						double bufferDelay = 0;
						double computationDelay = 0;
						
						//
						double avgResource = mipsUs/execList_e.size();
						
						// communication delay
						if(task.getMobileDeviceId() != i) {
							communicationDelay = (task.getCloudletLength() * 8) / 900000000;
						}
						
						// computation delay
						if(waitingList.size() > 0) {
							computationDelay = task.getCloudletLength() / avgResource;
							resource = avgResource;
						}else {
							computationDelay = task.getCloudletLength() / (edgeVmList.get(0).getMips() - mipsUs);
							resource = edgeVmList.get(0).getMips() - mipsUs;
						}
						
						// buffer delay
						if(edgeVmList.get(0).getMips() - mipsUs < task.getCloudletLength() / 2) {
							if(waitingList_e.size() > execList_e.size()) {
								for(int j = 0; j < execList_e.size(); j++) {
									bufferDelay += (execList_e.get(j).getRemainingCloudletLength() / eMips.get(j));
								}
								for(int j = 0; j < waitingList_e.size(); j++) {
									Task_Custom t = (Task_Custom)waitingList_e.get(j).getCloudlet();
									bufferDelay += (waitingList_e.get(j).getCloudletLength() / t.getAllocatedReousrce());
								}
							}else {
								for(int j = 0; j < waitingList_e.size(); j++) {
									bufferDelay += (execList_e.get(j).getRemainingCloudletLength() / eMips.get(j));
								}
							}
						}
						
						if(communicationDelay + bufferDelay + computationDelay < sDelay) {
							collaborationTarget = i;
							task.setAllocationResource(resource);
						}else {
							if(communicationDelay + bufferDelay + computationDelay > localDelay) {
								task.setAllocationResource(usage/execList.size());
								collaborationTarget = sourcePointLocation.getServingWlanId();
							}
						}
					}
					SimLogger.printLine("############Task list : " + LoadGeneratorModel.getTaskList());
					result = collaborationTarget;
				}
			}
		}
		else if(policy.equals("UTILIZATION_BASED")) { // least loaded
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(sourcePointLocation.getServingWlanId());
			List<ResCloudlet> exeList= vmArray.get(0).getCloudletScheduler().getCloudletExecList();
			List<Double> mipsShare = vmArray.get(0).getCloudletScheduler().getCurrentMipsShare();
			double usage = 0;
			double mips = 0;
			for(ResCloudlet rc : exeList) {
				Task_Custom tc = (Task_Custom)rc.getCloudlet();
				usage += tc.getAllocatedReousrce();
			}
			for(double m : mipsShare) {
				mips += m;
			}
			
			double minUtilization = usage / mips;
			int minIndex = sourcePointLocation.getServingWlanId();
			
			for(int i = 0; i < SimManager.getInstance().getEdgeServerManager().getDatacenterList().size(); i++) {
				List<EdgeVM> vmList = SimManager.getInstance().getEdgeServerManager().getVmList(i);
				List<ResCloudlet> eList = vmList.get(0).getCloudletScheduler().getCloudletExecList();
				double u = 0;
				double mip = 0;
				for(ResCloudlet rc : eList) {
					Task_Custom tc = (Task_Custom)rc.getCloudlet();
					u += tc.getAllocatedReousrce();
				}
				for(double m : mipsShare) {
					mip += m;
				}
				
				double eUtilization = u / mip;
				
				if(eUtilization < minUtilization) {
					minUtilization = eUtilization;
					minIndex = i;
				}
			}
			
			task.setAllocationResource(vmArray.get(0).getMips()); // 
			result = minIndex;
		}
		
		else if(policy.equals("DELAY_BASED")) { // delay based
			
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(sourcePointLocation.getServingWlanId());
			List<ResCloudlet> exeList= vmArray.get(0).getCloudletScheduler().getCloudletExecList(); 
			List<Double> mipsShare = vmArray.get(0).getCloudletScheduler().getCurrentMipsShare();
			
			
			NetworkModel networkModel = SimManager.getInstance().getNetworkModel();	
			
			
			double avgutils = SimManager.getInstance().getEdgeServerManager().getAvgUtilization(); 
			double var = 0;
			
			
			for(int i = 0; i<10; i++) {
				double tem=0;
				double curEdgeUtil = SimManager.getInstance().getEdgeServerManager().getEdgeUtilization(i);
				tem = (curEdgeUtil - avgutils)*(curEdgeUtil - avgutils);
				var+=tem;
			}
			var = var/10; // variation
			
			
			
			
			if(var<varThresh) {//////////////////////////////////////////////////////////////////////////////////// delay based operation			
				
				
				
				
				
				
				//System.out.println("11111111111111111 ");
				double communicationDelay = 0;
				double computationDelay = 0;
				int currentEdge = sourcePointLocation.getServingWlanId();
				double minDiff = Double.MAX_VALUE;
				int minIndex = 0;
		
				for(int i = 0; i<10; i++) {
					

					List<EdgeVM> vmList = SimManager.getInstance().getEdgeServerManager().getVmList(i); 
					List<ResCloudlet> eList = vmList.get(0).getCloudletScheduler().getCloudletExecList(); 
					Long[] remainLet = new Long[10];
					/*
					ArrayList<Long> remainLet = new ArrayList<Long>();
					*/
					for(int j = 0; j<10; j++) {
						remainLet[j]=(long)0; 
					}
					
					for(ResCloudlet rc : eList) {
						if(rc.getRemainingCloudletLength()!=0) {
							remainLet[i] = rc.getRemainingCloudletLength();//
						}
						 //System.out.println("RL : "+rc.getRemainingCloudletLength());
					}
					
					if(i!=currentEdge) {
						
						double currentEdgeUtil = SimManager.getInstance().getEdgeServerManager().getEdgeUtilization(i); // current edge server utilization
						communicationDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), i, task); 
						
						//System.out.println("arraylen : "+remainLet.size());
						
						computationDelay = remainLet[i]  / currentEdgeUtil;
						//computationDelay = 0;
						
						double diff=Double.MAX_VALUE;
						if(communicationDelay-computationDelay > 0) {
							diff = Math.abs(communicationDelay-computationDelay);
						}
						
						if(minDiff>diff) {
							minDiff = diff;
							minIndex = i;
						}
					}
				}
				task.setAllocationResource(vmArray.get(0).getMips());
				result = minIndex;
			}
			else {//resource based operation
			
				double usage = 0;
				double mips = 0;
				for(ResCloudlet rc : exeList) {
					Task_Custom tc = (Task_Custom)rc.getCloudlet();
					usage += tc.getAllocatedReousrce();
				}
				for(double m : mipsShare) {
					mips += m;
				}
				
				double minUtilization = usage / mips;
				int minIndex = sourcePointLocation.getServingWlanId();
				
				for(int i = 0; i < SimManager.getInstance().getEdgeServerManager().getDatacenterList().size(); i++) {
					List<EdgeVM> vmList = SimManager.getInstance().getEdgeServerManager().getVmList(i);
					List<ResCloudlet> eList = vmList.get(0).getCloudletScheduler().getCloudletExecList();
					double u = 0;
					double mip = 0;
					for(ResCloudlet rc : eList) {
						Task_Custom tc = (Task_Custom)rc.getCloudlet();
						u += tc.getAllocatedReousrce();
					}
					for(double m : mipsShare) {
						mip += m;
					}
					
					double eUtilization = u / mip;
					
					if(eUtilization < minUtilization) {
						minUtilization = eUtilization;
						minIndex = i;
					}
				}
				startFlag = 0;
				task.setAllocationResource(vmArray.get(0).getMips());
				result = minIndex;
			}
			

		}
		
		
		
		//else if(policy.equals("LOCALITY_BASE")) {
		else if(policy.equals("RANDOM_NEIGHBOR")) {
			List<EdgeVM> localVM = SimManager.getInstance().getEdgeServerManager().getVmList(sourcePointLocation.getServingWlanId());
			double localUsage = SimManager.getInstance().getEdgeServerManager().getEdgeUtilization(sourcePointLocation.getServingWlanId());
			result = sourcePointLocation.getServingWlanId(); 
			
			
			int[] nei1 = {1,2,3};
			int[] nei2 = {1,2,4,5};
			int[] nei3 = {1,3,4,7,8};
			int[] nei4 = {2,3,4,6,9};
			int[] nei5 = {2,5,6,8,10};
			int[] nei6 = {1,4,5,6,7};
			int[] nei7 = {3,6,7,9,10};
			int[] nei8 = {3,5,8,10};
			int[] nei9 = {4,7,9};
			int[] nei10 = {5,7,8,10};
			
		
			ArrayList<int[]> neimaps = new ArrayList<int[]>();
			neimaps.add(nei1);
			neimaps.add(nei2);
			neimaps.add(nei3);
			neimaps.add(nei4);
			neimaps.add(nei5);
			neimaps.add(nei6);
			neimaps.add(nei7);
			neimaps.add(nei8);
			neimaps.add(nei9);
			neimaps.add(nei10);
			
			int[] curnei = neimaps.get(result); 
			task.setAllocationResource((int)localVM.get(0).getMips()); 
			int randomIndex = SimUtils.getRandomNumber(0, curnei.length-1);
			result = curnei[randomIndex]-1;
			/*
			if(localUsage < 100) { 
				task.setAllocationResource((int)localVM.get(0).getMips()); 
				result = sourcePointLocation.getServingWlanId(); 
			}
			else { 
				
				int[] curnei = neimaps.get(result); 
				task.setAllocationResource((int)localVM.get(0).getMips()); 
				int randomIndex = SimUtils.getRandomNumber(0, curnei.length-1);
				result = curnei[randomIndex]-1;
			}	
			*/

		}	
		
		
		
		else if(policy.equals("LOCAL")) {
			List<EdgeVM> vmList = SimManager.getInstance().getEdgeServerManager().getVmList(sourcePointLocation.getServingWlanId());
			task.setAllocationResource(vmList.get(0).getMips());
			result = sourcePointLocation.getServingWlanId(); 
			SimLogger.printLine("Random Edge ID :"+ result);
		}
		
		
		else if(policy.equals("RANDOM")) {
			
			List<EdgeVM> vmList = SimManager.getInstance().getEdgeServerManager().getVmList(sourcePointLocation.getServingWlanId());
			task.setAllocationResource(vmList.get(0).getMips());
			result = SimUtils.getRandomNumber(0, 9);
			//SimLogger.printLine("Random Edge ID :"+ result);
		}
		
		
		return result;
}


	@Override
	public Vm getVmToOffload(Task_Custom task, int deviceId) {
		Vm selectedVM = null;
		
		if(deviceId < 10) {
//			double selectedVmCapacity = 0;
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(deviceId);
//			for(int vmIndex = 0; vmIndex < vmArray.size(); vmIndex++) {
//				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
//				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
//				
//				if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity) {
//					selectedVM = vmArray.get(vmIndex);
//					selectedVmCapacity = targetVmCapacity;
//				}
//			}
			selectedVM = vmArray.get(0);
		}else {
			SimLogger.printLine("Unknown device id! The simulation has been terminated.");
			System.out.println("device id : " + deviceId);
			System.exit(0);
		}
		
		return selectedVM;
	}
	
	public Vm getVmToOffload(Task task, int deviceId, int edgeId) {
		Vm selectedVM = null;
		
		if(deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
//			double selectedVmCapacity = 0;
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(edgeId);
//			for(int vmIndex = 0; vmIndex < vmArray.size(); vmIndex++) {
//				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
//				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
//				if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity) {
//					selectedVM = vmArray.get(vmIndex);
//					selectedVmCapacity = targetVmCapacity;
//				}
//			}
			selectedVM = vmArray.get(0);
		}else if(deviceId == SimSettings.EDGE_ORCHESTRATOR_ID) {
			//���� ����
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(edgeId);
			selectedVM = vmArray.get(0);
		}else {
			SimLogger.printLine("Unknown device id! The simulation has been terminated.");
			System.exit(0);
		}
		
		System.out.println("Edge index : " + edgeId);
		
		return selectedVM;
	}

	@Override
	public void processEvent(SimEvent arg0) {
		// TODO Auto-generated method stub
		System.out.println("processingEvent");
		
	}

	@Override
	public void shutdownEntity() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startEntity() {
		// TODO Auto-generated method stub
		
	}
	
	protected ArrayList<Integer> sortingByUtilization(ArrayList<Integer> edgeSet){
		ArrayList<Double> sorting = new ArrayList<Double>();
		for(int i=0; i < edgeSet.size();i++) {
			double usage = 0;
			List<EdgeVM> vm = SimManager.getInstance().getEdgeServerManager().getVmList(i);
			List<Double> mips= vm.get(0).getCloudletScheduler().getCurrentMipsShare();
			
			if(mips != null) {
				for(double m : mips) {
					usage += m;
				}
			}else {
				vm.get(0).setCurrentAllocatedMips(mips);
			}
			
			sorting.add(vm.get(0).getMips() - usage);
		}
		
		for(int i = sorting.size() - 1; i > 0; i--) {
			for(int j = 0; j < i; j++) {
				if(sorting.get(j) > sorting.get(i)) {
					double temp = 0;
					int edgeindex = 0;
					temp = sorting.get(j);
					sorting.set(j, sorting.get(i));
					sorting.set(i, temp);
					edgeindex = edgeSet.get(j);
					edgeSet.set(j, edgeSet.get(i));
					edgeSet.set(i, edgeindex);
				}
			}
		}
		
		return edgeSet;
	}
	
	protected ArrayList<Integer> sortByBuffer(ArrayList<Integer> edgeSet){
		ArrayList<Integer> sorting = new ArrayList<Integer>();
		
		for(int i = 0; i < edgeSet.size(); i++) {
			List<EdgeVM> vm = SimManager.getInstance().getEdgeServerManager().getVmList(i);
			List<ResCloudlet> waiting =  vm.get(0).getCloudletScheduler().getCloudletWaitingList();
			sorting.add(waiting.size());
		}
		
		for(int i = sorting.size() - 1; i > 0; i--) {
			for(int j = 0; j < i; j++)
				if(sorting.get(j) < sorting.get(i)) {
					int temp1 = 0, temp2 = 0;
					temp1 = sorting.get(j);
					temp2 = edgeSet.get(j);
					sorting.set(j, sorting.get(i));
					sorting.set(i, temp1);
					edgeSet.set(j, edgeSet.get(i));
					edgeSet.set(i, temp2);
				}
		}
				
		return edgeSet;
	}

}
