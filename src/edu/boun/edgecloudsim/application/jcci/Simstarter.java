package edu.boun.edgecloudsim.application.jcci;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class Simstarter {
	
	private static double weightA = 0.5;
	private static double weightB = 0.5;
	public static int iter = 1;
	
	public void SimInit() {
		// TODO Auto-generated method stub
		Log.disable();
		
		SimLogger.enablePrintLog();
		
		int iterationNumber = 1;
		String configFile = "";
		String outputFolder = "";
		String edgeDevicesFile = "";
		String applicationsFile = "";
		
		//ï¿½ï¿½ï¿½ï¿½ ï¿½Ê¿ï¿½
		SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
		configFile = "scripts/jcci/config/default_config.properties";
		applicationsFile = "scripts/jcci/config/applications.xml";
		edgeDevicesFile = "scripts/jcci/config/edge_devices.xml";
//				outputFolder = "sim_results/ite" + iterationNumber;
		outputFolder = "sim_results/local";
		
		
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false) {
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		if(SS.getFileLoggingEnabled()){
			SimLogger.enableFileLog();
			SimUtils.cleanOutputFolder(outputFolder);
		}

		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");
		
		Simstart(SS, now, df, iterationNumber, outputFolder, SimulationStartDate);
				
	}
	
	public double Simstart(SimSettings SS, String now, DateFormat df, int iterationNumber, String outputFolder, Date SimulationStartDate) {
		SimLogger.printLine("/////////////////////////////");
		SimLogger.printLine("Gen : " + iter);
		SimLogger.printLine("/////////////////////////////");
		iter++;
		
		// ¿©±â¼­ ¿§Áö¼­¹ö°¡ ÀÌ¹Ì °áÁ¤µÇ¾î ÀÖ¾î¾ßÇÔ.
		double result = 0;

		for(int j = SS.getMinNumOfMobileDev(); j <= SS.getMaxNumOfMobileDev(); j += SS.getMobileDevCounterSize()){ 
			for(int k = 0; k < SS.getSimulationScenarios().length; k++) {
				for(int i = 0; i < SS.getOrchestratorPolicies().length; i++) {
					String simScenario = SS.getSimulationScenarios()[k];
					String orchestratorPolicy = SS.getOrchestratorPolicies()[i];
					Date ScenarioStartDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioStartDate);
					
					SimLogger.printLine("Scenario started at " + now);
					SimLogger.printLine("Scenario: " + simScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
					SimLogger.printLine("Duration: " + SS.getSimulationTime()/60 + " min (warm up period: "+ SS.getWarmUpPeriod()/60 +" min) - #devices: " + j);
					SimLogger.getInstance().simStarted(outputFolder,"SIMRESULT_" + simScenario + "_"  + orchestratorPolicy + "_" + j + "DEVICES");
					
					try {
						int num_user = 2;
						Calendar calender = Calendar.getInstance();
						boolean trace_flag = false;
						
						CloudSim.init(num_user, calender, trace_flag, 0.01);
						
						ScenarioFactory sampleFactory = new SampleScenarioFactory(j, SS.getSimulationTime(), 
								orchestratorPolicy, simScenario);
						
						SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);
						
						manager.startSimulation();
					}catch(Exception e) {
						SimLogger.printLine("The simulation has been terminated due to an unexpected error");
						e.printStackTrace();
						System.exit(0);
					}
					
					Date ScenarioEndDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioEndDate);
					SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
					SimLogger.printLine("----------------------------------------------------------------------");
				}
			}
		}
		
		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
		
		
		
		
		
		result = SimLogger.getInstance().getAvgSuccessRate()*weightA - SimLogger.getInstance().getAvgServiceTime()*weightB;
		
		return result;
		
	}

}
