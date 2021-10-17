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
	
	//20211016 HJ timeslot 안에 들어온 태스크들 저장소.
	private ArrayList<Task_Custom> taskQueue = new ArrayList<Task_Custom>();
	private double timeSlotStartTime = 0;
	private double timeSlotThresh = 3;
	//timeslot 시작 시간
	
	
	public CustomMobileDeviceManager() throws Exception{
		
	}

	@Override
	public void initialize() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public UtilizationModel getCpuUtilizationModel() {
		// TODO Auto-generated method stub
		//Ȯ�� �ʿ�
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
	
	// 20211016 HJ priority 설정 함수
	private ArrayList<Task_Custom> setPriority() {
		
		
//		ArrayList<Integer> indexList = new ArrayList<>();
//		ArrayList<Double> throughputList = new ArrayList<>();
		Map<Double, Task_Custom> taskMap = new TreeMap<Double, Task_Custom>(); // throughhput으로 소팅하기 위해 만든 맵
		
		for(int i = 0; i<taskQueue.size(); i++) { // Priority 계산
			long _size = taskQueue.get(i).getTaskSize();
			long _deadline = taskQueue.get(i).getTaskDeadline();
			double _throughput = (double)_size/(double)_deadline;
			taskMap.put(_throughput, taskQueue.get(i)); // treemap 이라 key에 따라 알아서 정렬
		}
		
		
		
		Collection<Task_Custom> values = taskMap.values(); // value 만 뽑기위해서 
		ArrayList<Task_Custom> PritizedTasks = new ArrayList<Task_Custom>(values); // value값 다시 arraylist 형태로 추

//		SimLogger.printLine("Sorted antry : " + taskMap.keySet());

		
		
		
		
		return PritizedTasks;
	}

	
	@Override
	// 20211016 HJ Timeslot 구현하기 위해 변경됨. -> CloudSim.clock() 사용해서 구현하기.
	public void submitTask(TaskProperty edgeTask) {
		// TODO Auto-generated method stub
		//System.out.println("submitTask");
		int vmType = 0;
		int nextEvent = 0;
		int nextDeviceForNetworkModel;
		NETWORK_DELAY_TYPES delayType;
		double delay = 0;
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		Task_Custom task = createTask(edgeTask);
		
		Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
		
		task.setSubmittedLocation(currentLocation);
		
		SimLogger.getInstance().addLog(task.getMobileDeviceId(), 
				task.getCloudletId(), 
				task.getTaskType(), 
				(int)task.getCloudletLength(),
				(int)task.getCloudletFileSize(),
				(int)task.getCloudletOutputSize());
		
		//�ۼ� �ʿ� ���ɽ�Ʈ������ �θ��� �κ�. // �������� �� edge id ��
		// 20211016 HJ timeslot 구현 위해 이 아래 구현
		// 원본은 else 안에 들어
		if(taskQueue.size() == 0)
			timeSlotStartTime = CloudSim.clock();
		if(CloudSim.clock() - timeSlotStartTime < timeSlotThresh) { // timeslot 임계시간 안지나면 태스크는 걍 큐에 넣
			taskQueue.add(task);
		}
		else { // timeslo 임계 시간 지난 경우 -> priority 설정하고 sorting, 후 for 돌려서 하나씩 실행해줌
//			SimLogger.printLine("Task : " + taskQueue);
			ArrayList<Task_Custom> PritizedTasks = setPriority(); // priority 순서대로(높->낮) 으로 소팅됨
			taskQueue.clear(); //큐 초기화
			
			
			for(int i = 0; i<PritizedTasks.size(); i++) {
								
				
				
				
				
				
			}
			
		}
		//original code
		int nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task); 
		
//		System.out.println("nextHopId : " + nextHopId);
		
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
