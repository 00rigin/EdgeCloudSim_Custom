package edu.boun.edgecloudsim.application.jcci;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Collection;

import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.Task_Custom;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.TaskProperty;

//import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;


public class CustomMobileDeviceManager extends MobileDeviceManager{
	private static final int BASE = 1000;
	
	private static final int UPDATE_MM1_QUEUE_MODEL = BASE + 1;
	private static final int REQUEST_RECEIVED_BY_EDGE_DEVICE = BASE + 2;
	private static final int RESPONSE_RECEIVED_BY_EDGE_DEVICE = BASE +3;
	
	private static final double MM1_QUEUE_MODEL_UPDATE_INTEVAL = 5;
	
	private int taskIDCounter = 0;
	
	//20211016 HJ Made some var for timeslot
	private ArrayList<Task_Custom> taskQueue = new ArrayList<Task_Custom>();
	private double timeSlotStartTime = 0;
	private double timeSlotThresh = 3;
	
	
	
	public CustomMobileDeviceManager() throws Exception{
		
	}

	@Override
	public void initialize() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public UtilizationModel getCpuUtilizationModel() {
		// TODO Auto-generated method stub
		
		return new CpuUtilizationModel_Custom();
	}
	
	@Override
	public void startEntity() {
		super.startEntity();
		schedule(getId(),SimSettings.CLIENT_ACTIVITY_START_TIME + MM1_QUEUE_MODEL_UPDATE_INTEVAL, UPDATE_MM1_QUEUE_MODEL);
	}
	
	//
	protected void processCloudletReturn(SimEvent ev) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task_Custom task = (Task_Custom)ev.getData();
		double WanDelay = 0;
		
		SimLogger.getInstance().taskExecuted(task.getCloudletId());
		
		if(task.getAssociatedDatacenterId() == SimSettings.EDGE_ORCHESTRATOR_ID) {
			WanDelay = networkModel.getUploadDelay(SimSettings.EDGE_ORCHESTRATOR_ID, task.getMobileDeviceId(), task);
			SimLogger.getInstance().setUploadDelay(task.getCloudletId(), WanDelay, NETWORK_DELAY_TYPES.WAN_DELAY);
		}
		
		int nextEvent = RESPONSE_RECEIVED_BY_EDGE_DEVICE;
		
		schedule(getId(), WanDelay, nextEvent, task);
	}
	
	protected void processOtherEvent(SimEvent ev) {
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(0);
			return;
		}
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
//		System.out.println("evTag:" + ev.getTag());
		
		switch(ev.getTag()) {
			case UPDATE_MM1_QUEUE_MODEL:
			{
				((CustomNetworkModel)networkModel).updateMM1QueueModel();
				schedule(getId(), MM1_QUEUE_MODEL_UPDATE_INTEVAL, UPDATE_MM1_QUEUE_MODEL);
				
				break;
			}
			case REQUEST_RECEIVED_BY_EDGE_DEVICE:
			{
				Task_Custom task = (Task_Custom)ev.getData();
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				submitTaskToVm(task, SimSettings.VM_TYPES.EDGE_VM);
				break;
			}
			case RESPONSE_RECEIVED_BY_EDGE_DEVICE:
			{
				Task_Custom task = (Task_Custom)ev.getData();
				
				SimLogger.getInstance().taskEnded(task.getCloudletId(), CloudSim.clock());
				break;
			}
			default:
				SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
				System.exit(0);
				break;
		}
	}
	
	private void submitTaskToVm(Task_Custom task, SimSettings.VM_TYPES vmType) {
		schedule(getVmsToDatacentersMap().get(task.getVmId()), 0, CloudSimTags.CLOUDLET_SUBMIT, task);

		SimLogger.getInstance().taskAssigned(task.getCloudletId(),
				task.getAssociatedDatacenterId(),
				task.getAssociatedHostId(),
				task.getAssociatedVmId(),
				vmType.ordinal());
	}
	
	// 20211016 HJ priority 
	// Sort the task by processing trhoughput
	private ArrayList<Task_Custom> setPriority() {
		
		Map<Double, Task_Custom> taskMap = new TreeMap<Double, Task_Custom>();
		
		for(int i = 0; i<taskQueue.size(); i++) { // Priority 
			long _size = taskQueue.get(i).getTaskSize();
			long _deadline = taskQueue.get(i).getTaskDeadline();
			double _throughput = (double)_size/(double)_deadline;
			taskMap.put(_throughput, taskQueue.get(i)); // By using treemap, sorting in done automatically
		}
		
		Collection<Task_Custom> values = taskMap.values(); 
		ArrayList<Task_Custom> PritizedTasks = new ArrayList<Task_Custom>(values); 
//		SimLogger.printLine("Sorted antry : " + taskMap.keySet());

		
		
		
		
		return PritizedTasks;
	}

	
	@Override
	// 20211016 HJ Timeslot 
	public void submitTask(TaskProperty edgeTask) {
		// TODO Auto-generated method stub
		//System.out.println("submitTask");
		int vmType = 0;
		int nextEvent = 0;
		int nextDeviceForNetworkModel;
		NETWORK_DELAY_TYPES delayType;
		double delay = 0;
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		Task_Custom _task = createTask(edgeTask);
		
		Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(_task.getMobileDeviceId(), CloudSim.clock());
		
		_task.setSubmittedLocation(currentLocation);
		
		SimLogger.getInstance().addLog(_task.getMobileDeviceId(), 
				_task.getCloudletId(), 
				_task.getTaskType(), 
				(int)_task.getCloudletLength(),
				(int)_task.getCloudletFileSize(),
				(int)_task.getCloudletOutputSize());
		
		
		// 20211016 HJ timeslot 
		
		if(taskQueue.size() == 0)
			timeSlotStartTime = CloudSim.clock();
		if(CloudSim.clock() - timeSlotStartTime < timeSlotThresh) {
			taskQueue.add(_task);
		}
		else { // larger than time th
//			SimLogger.printLine("Task : " + taskQueue);
			ArrayList<Task_Custom> PritizedTasks = setPriority(); // prioritized Tasks.
			taskQueue.clear(); //init the Q
			
			
			for(int i = 0; i<PritizedTasks.size(); i++) {
				Task_Custom task = PritizedTasks.get(i);
				
				//original code
				int nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task); 
				
//				System.out.println("nextHopId : " + nextHopId);
				
				delay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task);
				vmType = SimSettings.VM_TYPES.EDGE_VM.ordinal();
				delayType = NETWORK_DELAY_TYPES.WAN_DELAY;
				nextEvent = REQUEST_RECEIVED_BY_EDGE_DEVICE;
				nextDeviceForNetworkModel = SimSettings.EDGE_ORCHESTRATOR_ID;
				
				Vm selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task, nextHopId);
				
				if(selectedVM != null) {
					task.setAssociatedDatacenterId(nextHopId);
					
					task.setAssociatedHostId(selectedVM.getHost().getId());
					
					task.setAssociatedVmId(selectedVM.getId());
					
					getCloudletList().add(task);
					bindCloudletToVm(task.getCloudletId(), selectedVM.getId());
					
					networkModel.uploadStarted(currentLocation, nextDeviceForNetworkModel);
					
					SimLogger.getInstance().taskStarted(task.getCloudletId(), CloudSim.clock());
					SimLogger.getInstance().setUploadDelay(task.getCloudletId(), delay, delayType);

					schedule(getId(), delay, nextEvent, task);
				}
				
				
			}
			
		}
		

		
		

		
	}
	
//	protected void processEvent(SimEvent ev) {
//		if (ev == null) {
//			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
//			System.exit(0);
//			return;
//		}
//		
//		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
//	}
	
	private Task_Custom createTask(TaskProperty edgeTask) {
		UtilizationModel utilizationModel = new UtilizationModelFull(); /*UtilizationModelStochastic*/
		UtilizationModel utilizationModelCPU = getCpuUtilizationModel();

		Task_Custom task = new Task_Custom(edgeTask.getMobileDeviceId(), ++taskIDCounter,
				edgeTask.getLength(), edgeTask.getPesNumber(),
				edgeTask.getInputFileSize(), edgeTask.getOutputFileSize(),
				utilizationModelCPU, utilizationModel, utilizationModel, 0);
		
		//set the owner of this task
		task.setUserId(this.getId());
		task.setTaskType(edgeTask.getTaskType());
		task.setTaskSize(edgeTask.getLength());
		task.setDeadline(edgeTask.getTaskDeadline());
		
		if (utilizationModelCPU instanceof CpuUtilizationModel_Custom) {
			((CpuUtilizationModel_Custom)utilizationModelCPU).setTask(task);
		}
		
		return task;
	}
}
