/*
 * Title:        EdgeCloudSim - Task
 * 
 * Description: 
 * Task adds app type, task submission location, mobile device id and host id
 * information to CloudSim's Cloudlet class.
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.utils.Location;

public class Task_Custom extends Cloudlet {
	private Location submittedLocation;
	private double creationTime;
	private int type;
	private int mobileDeviceId;
	private int hostIndex;
	private int vmIndex;
	private int datacenterId;
	private double allocatedResource;
	// KSC�� ���� �߰���
	private long taskSize;
	private long deadline;
	private int priority;

	public Task_Custom(int _mobileDeviceId, int cloudletId, long cloudletLength, int pesNumber,
			long cloudletFileSize, long cloudletOutputSize,
			UtilizationModel utilizationModelCpu,
			UtilizationModel utilizationModelRam,
			UtilizationModel utilizationModelBw,
			int allocationResource) {
		super(cloudletId, cloudletLength, pesNumber, cloudletFileSize,
				cloudletOutputSize, utilizationModelCpu, utilizationModelRam,
				utilizationModelBw);
		
		mobileDeviceId = _mobileDeviceId;
		creationTime = CloudSim.clock();
		allocatedResource = allocationResource;
	}
	
//	 20211014 HJ �߰�
	public void setTaskSize(long _size) {
		taskSize = _size;
	}
	
	public void setDeadline(long _deadline) {
		deadline = _deadline;
	}
	
	public void setPriority(int _class_) {
		priority = _class_;
	}
	
	public long getTaskSize() {
		return taskSize;
	}
	public long getTaskDeadline() {
		return deadline;
	}
	public int getTaskPriority() {
		return priority;
	}
	
	public void setSubmittedLocation(Location _submittedLocation){
		submittedLocation =_submittedLocation;
	}

	public void setAssociatedDatacenterId(int _datacenterId){
		datacenterId=_datacenterId;
	}
	
	public void setAssociatedHostId(int _hostIndex){
		hostIndex=_hostIndex;
	}

	public void setAssociatedVmId(int _vmIndex){
		vmIndex=_vmIndex;
	}
	
	public void setTaskType(int _type){
		type=_type;
	}

	public int getMobileDeviceId(){
		return mobileDeviceId;
	}
	
	public Location getSubmittedLocation(){
		return submittedLocation;
	}
	
	public int getAssociatedDatacenterId(){
		return datacenterId;
	}
	
	public int getAssociatedHostId(){
		return hostIndex;
	}

	public int getAssociatedVmId(){
		return vmIndex;
	}
	
	public int getTaskType(){
		return type;
	}
	
	public double getCreationTime() {
		return creationTime;
	}
	
	public double getAllocatedReousrce(){
		return allocatedResource;
	}
	
	public void setAllocationResource(double resource) {
		allocatedResource = resource;
	}
}
