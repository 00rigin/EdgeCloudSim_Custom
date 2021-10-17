package edu.boun.edgecloudsim.application.jcci;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.DefaultMobileServerManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.task_generator.IdleActiveLoadGenerator;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.mobility.NomadicMobility;

public class SampleScenarioFactory implements ScenarioFactory{
	private int numOfMobileDevice;
	private double simulationTime;
	private String orchestratorPolicy;
	private String simScenario;
	
	SampleScenarioFactory(int _numOfMobileDevice, 
			double _simulationTime,
			String _orchestratorPolicy,
			String _simScenario){
		orchestratorPolicy = _orchestratorPolicy;
		numOfMobileDevice = _numOfMobileDevice;
		simulationTime = _simulationTime;
		simScenario = _simScenario;
	}

	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		// TODO Auto-generated method stub
		return new IdleActiveLoadGenerator(numOfMobileDevice,
				simulationTime,
				simScenario);
	}

	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		// TODO Auto-generated method stub
		return new CustomEdgeOrchestrator(orchestratorPolicy, simScenario);
	}

	@Override
	public MobilityModel getMobilityModel() {
		// TODO Auto-generated method stub
		return new NomadicMobility(numOfMobileDevice, simulationTime);
	}

	@Override
	public NetworkModel getNetworkModel() {
		// TODO Auto-generated method stub
		// 내가 수정한 걸로 할 것
		return new CustomNetworkModel(numOfMobileDevice, simScenario);
	}

	@Override
	public EdgeServerManager getEdgeServerManager() {
		// TODO Auto-generated method stub
		return new CustomEdgeServerManager();
	}

	@Override
	public CloudServerManager getCloudServerManager() {
		// TODO Auto-generated method stub
		return new DefaultCloudServerManager();
	}

	@Override
	public MobileServerManager getMobileServerManager() {
		// TODO Auto-generated method stub
		// 수정 필요
		return new DefaultMobileServerManager();
	}

	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		// TODO Auto-generated method stub
		return new CustomMobileDeviceManager();
	}

}
