package Jenetic;

import io.jenetics.BitChromosome;
import io.jenetics.BitGene;
import io.jenetics.Chromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Optimize;
import io.jenetics.RouletteWheelSelector;
import io.jenetics.TournamentSelector;
import io.jenetics.DoubleGene;
import io.jenetics.EliteSelector;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.engine.Engine;
import io.jenetics.engine.Limits;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.util.Factory;
import io.jenetics.util.IntRange;

import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.Random;

import Jenetic.CustomSelector;
import edu.boun.edgecloudsim.application.jcci.Simstarter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
//
//import org.cloudbus.cloudsim.Log;
//import org.cloudbus.cloudsim.core.CloudSim;
//
//import edu.boun.edgecloudsim.core.ScenarioFactory;
//import edu.boun.edgecloudsim.core.SimManager;
//import edu.boun.edgecloudsim.core.SimSettings;
//import edu.boun.edgecloudsim.utils.SimLogger;
//import edu.boun.edgecloudsim.utils.SimUtils;

public class Jenetic{
	double weightA = 0.5;
	double weightB = 0.5;

		
	// ���� ����. �� gene�� ���̸� �ִ�ȭ �ϵ��� ¥�°�.
	private static Double calcValue(Genotype<IntegerGene> gt) {
		// gt�� [[[gene],[gene],.....,[gene]]] �÷� �����Ǿ�����
		
		Double res=(double) 0;
		
		for(int i = 1; i<gt.get(0).length(); i++) {
			res += (gt.get(0).get(i).intValue()-gt.get(0).get(i-1).intValue());
		}
		res = res/gt.get(0).length();
		return res;
		
	}
	
	private static Double Fitness(Genotype<IntegerGene> gt) {
		Double res = (double) 0;
		
		
		
		// ���� �ȿ��� �ù��� ���� �ؾ��ҵ�...!
		
		
		
		
		return res;
	}
	
	// select method implementation
	
	
	
	public static void main(String[] args) {
		
		/////////// Edge Cloud Sim ���� ���� �κ� ////////////////////////
//		Log.disable();
//		
//		SimLogger.enablePrintLog();
//		
//		int iterationNumber = 1;
//		String configFile = "";
//		String outputFolder = "";
//		String edgeDevicesFile = "";
//		String applicationsFile = "";
//		
//		//���� �ʿ�
//		SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
//		configFile = "scripts/jcci/config/default_config.properties";
//		applicationsFile = "scripts/jcci/config/applications.xml";
//		edgeDevicesFile = "scripts/jcci/config/edge_devices.xml";
////				outputFolder = "sim_results/ite" + iterationNumber;
//		outputFolder = "sim_results/local";
//		
//		
//		SimSettings SS = SimSettings.getInstance();
//		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false) {
//			SimLogger.printLine("cannot initialize simulation settings!");
//			System.exit(0);
//		}
//		
//		if(SS.getFileLoggingEnabled()){
//			SimLogger.enableFileLog();
//			SimUtils.cleanOutputFolder(outputFolder);
//		}
//
//		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
//		Date SimulationStartDate = Calendar.getInstance().getTime();
//		String now = df.format(SimulationStartDate);
//		SimLogger.printLine("Simulation started at " + now);
//		SimLogger.printLine("----------------------------------------------------------------------");
//		
//		
//		Simstarter simulator = new Simstarter();
//		
//		simulator.Simstart(SS, now, df, iterationNumber, outputFolder, SimulationStartDate);
		//////////////////////////////////////////////////////////////
		
		
		// Genetype �����ϱ�. �ϴ� integer�� ������ chromosome
		Factory<Genotype<IntegerGene>> gt = 
				Genotype.of(IntegerChromosome.of(1,9, 10)); // min,max,len ���� ���� Integer chromosome ����.....
		System.out.println("start :" + ((Genotype<IntegerGene>) gt).get(0));
		
		System.out.println("Value : " + calcValue((Genotype<IntegerGene>) gt));
		
		// ���� ȯ�� ����
		Engine<IntegerGene, Double> engine = Engine
				.builder(
						Jenetic::Fitness, 
						gt)
				.populationSize(500)
//				.survivorsSelector(new CustomSelector<>()) // ���⼭ custom selector ����ϱ� 
				.survivorsSelector(new EliteSelector<>()) // ���⼭ custom selector ����ϱ� 
				.optimize(Optimize.MAXIMUM)
				.build();
		
		// ����
		Genotype<IntegerGene> result = engine.stream()
				.limit(100)
				.collect(EvolutionResult.toBestGenotype());
		
		System.out.println("Result :" + result);
		System.out.println("Value : " + calcValue((Genotype<IntegerGene>) result));
        System.out.println("--------------------------");
		

	}

}
