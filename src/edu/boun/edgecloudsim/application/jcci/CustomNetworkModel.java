package edu.boun.edgecloudsim.application.jcci;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.Task_Custom;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

public class CustomNetworkModel extends NetworkModel{
	public static enum NETWORK_TYPE {WAN};
	public static double BW = 1000*1024; //Kbps // ES°£ BW
	private CustomNetworkTopology topology;
	
	private double lastMM1QueueUpdateTime;
	private double WanPoissonMeanForDownload[];
	private double WanPoissonMeanForUpload[];
	
	private double avgWanTaskInputSize;
	private double avgWanTaskOutputSize;
	
	private double totalWanTaskInputSize;
	private double totalWanTaskOutputSize;
	private double numOfWanTaskForDownload;
	private double numOfWanTaskForUpload;

	public CustomNetworkModel(int _numberOfMobileDevices, String _simScenario) {
		super(_numberOfMobileDevices, _simScenario);
		// TODO Auto-generated constructor stub
		topology = new CustomNetworkTopology();
	}

	@Override
	public void initialize() {
		// TODO Auto-generated method stub
		int numOfApp = SimSettings.getInstance().getTaskLookUpTable().length;
		SimSettings SS = SimSettings.getInstance();
		for(int taskIndex = 0; taskIndex < numOfApp; taskIndex++) {
			if(SS.getTaskLookUpTable()[taskIndex][0] == 0) {
				SimLogger.printLine("Usage percentage of task " + taskIndex + " is 0! Terminating simulation...");
				System.exit(0);
			}
		}
		
		lastMM1QueueUpdateTime = SimSettings.CLIENT_ACTIVITY_START_TIME;
	}

	@Override
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task_Custom task) {
		// TODO Auto-generated method stub
		double delay = 0;
//		System.out.println(destDeviceId);
		Location sourcePointLocation = SimManager.getInstance().getMobilityModel().getLocation(sourceDeviceId, CloudSim.clock());
		int sourceId = sourcePointLocation.getServingWlanId();
		
//		System.out.println("destDeviceId: " + destDeviceId + "sourceId: " + sourceId);
		
		if(destDeviceId == sourceId) {
			return delay;
		}
		else{
			int route[] = topology.dijkstra(sourceId, destDeviceId);
			
			delay = getWanUploadDelay(task.getCloudletFileSize(), route.length);
		}
		
		return delay;
	}

	@Override
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task_Custom task) {
		// TODO Auto-generated method stub
		double delay = 0;
		
		Location sourcePointLocation = SimManager.getInstance().getMobilityModel().getLocation(sourceDeviceId, CloudSim.clock());
		int sourceId = sourcePointLocation.getServingWlanId();
		
		if(sourceDeviceId == destDeviceId && sourceDeviceId == SimSettings.GENERIC_EDGE_DEVICE_ID){
			return delay;
		}else {
			
			int route[] = topology.dijkstra(sourceId, destDeviceId);
			
			delay = getWanUploadDelay(task.getCloudletFileSize(), route.length);
		}
		
//		System.out.println("down delay:" + delay);
		
		return delay;
	}

	@Override
	public void uploadStarted(Location accessPointLocation, int destDeviceId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void uploadFinished(Location accessPointLocation, int destDeviceId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void downloadStarted(Location accessPointLocation, int sourceDeviceId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void downloadFinished(Location accessPointLocation, int sourceDeviceId) {
		// TODO Auto-generated method stub
		
	}
	
	private double getWanDownloadDelay(double dataSize, int hop) {
		double taskSizeInKb = dataSize * (double)8;
		double result = 0;
		
		result = ((double)taskSizeInKb / BW) * hop;
		
//		System.out.println("delay:" + result + " hop: " + hop);
		
		return result;
	}
	
	private double getWanUploadDelay(double dataSize, int hop) {
		return getWanDownloadDelay(dataSize, hop);
	}
	
	private double calculateMM1(double propagationDelay, double bandwidth) {
		double result = 0;
		return result;
	}
	
	public void updateMM1QueueModel() {
		double lastInterval = CloudSim.clock() - lastMM1QueueUpdateTime;
		lastMM1QueueUpdateTime = CloudSim.clock();
		
		//
	}
	
}
