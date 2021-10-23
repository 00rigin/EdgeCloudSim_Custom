package edu.boun.edgecloudsim.application.jcci;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.UtilizationModelFull;
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
import edu.boun.edgecloudsim.application.jcci.CustomNetworkModel;

public class CustomEdgeOrchestrator extends EdgeOrchestrator{
	
	private int numberOfHost;
	private double varThresh;
	private int startFlag = 0;
	private boolean flg = false;
	public int resChecker[] = {0,0,0,0,0,0,0,0,0,0};
	
	
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
			
//			List<EdgeVM> localVM = SimManager.getInstance().getEdgeServerManager().getVmList(sourcePointLocation.getServingWlanId()); // vm list : ÇöÀç´Â 1°³
			
			double localUsage = SimManager.getInstance().getEdgeServerManager().getEdgeUtilization(sourcePointLocation.getServingWlanId()); // ÇöÀç ¿§Áö¼­¹öÀÇ À¯Æ¿¶óÀÌÁ¦ÀÌ¼Ç
			NetworkModel networkModel = SimManager.getInstance().getNetworkModel();	
			result = 0; // Çù¾÷ ´ë»ó ÃÊ±âÈ­ (ÇöÀç ¿§Áö¼­¹ö ¾ÆÀÌµð)
			
			ArrayList<Integer> edge = new ArrayList<>(Arrays.asList(0,1,2,3,4,5,6,7,8,9)); // NUMBER OF EDGE SERVER
			int numofEdge = edge.size();
			
			//dummy task to simulate a task with 1 Mbit file size to upload and download 
			Task_Custom dummyTask = new Task_Custom(0, 0, 0, 0, 128, 128, new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull(), 0);
			
			// TaskSize
			long taskSize = task.getTaskSize();
			// TaskDeadline
			double deadline = task.getTaskDeadline();
			
			
			for(int i = 0; i < edge.size(); i++) {
				List<EdgeVM> edgeVmList = SimManager.getInstance().getEdgeServerManager().getVmList(i);
				List<Double> eMips = edgeVmList.get(0).getCloudletScheduler().getCurrentMipsShare();
				List<ResCloudlet> execList_e = edgeVmList.get(0).getCloudletScheduler().getCloudletExecList();
				List<List<ResCloudlet>> waitingList_e = edgeVmList.get(0).getWaitingList();
				
				double processingThroughput = 0;
				
				double _max_ = 0;
				
				double Qdelay = 0;
				
				double communicationDelay = 0;

				//Buffer delay
				for(int j = 0; j<execList_e.size(); j++) 
					Qdelay += (execList_e.get(j).getRemainingCloudletLength() / eMips.get(0));
				
				
				int taskPri = task.getTaskPriority();
				
				// ÅÂ½ºÅ© ÀÚ½ÅÀÇ »óÀ§ Å¬·¡½º + ÀÚ½ÅÀÇ Å¬·¡½º¿¡ ´ëÇÑ Å¥À× µô·¹ÀÌ
				for(int j = 0; j<taskPri; j++) {
					try {
						for(int k = 0; k<waitingList_e.get(j).size(); k++) {
							Task_Custom t = (Task_Custom)waitingList_e.get(i).get(j).getCloudlet();
							Qdelay += (waitingList_e.get(j).get(k).getCloudletLength() / t.getAllocatedReousrce());
						}
					}
					catch(IndexOutOfBoundsException e) {
						Qdelay += 0;
					}
				}
				
//				System.out.println(Qdelay);
				 // Transmission delay
				communicationDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), i, dummyTask);
				
				processingThroughput = (double)taskSize*0.1 /(deadline - 10*Qdelay - 10*communicationDelay);
				
//				if(startFlag>200 && startFlag<300) {
//				
//					System.out.println("wnaDelay : "+ communicationDelay + " | qTime : "+Qdelay+" | taskSize : "+taskSize+" | deadline : "+deadline+" | PT : "+processingThroughput+" | result : "+result);
//					
//				}

				if(processingThroughput > _max_ && processingThroughput>0) {
					processingThroughput = _max_;
					result = i;
				}
			}
			
//			resChecker[result]++;
//			
//			if(startFlag > 1000) {
//				for(int i = 0; i<10; i++) {
//					System.out.print(resChecker[i] + " ");
//				}
//				System.out.println();
//			}
//
			startFlag++;

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
			//ï¿½ï¿½ï¿½ï¿½ ï¿½ï¿½ï¿½ï¿½
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(edgeId);
			selectedVM = vmArray.get(0);
		}else {
			SimLogger.printLine("Unknown device id! The simulation has been terminated.");
			System.exit(0);
		}
		
//		System.out.println("Edge index : " + edgeId);
		
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
