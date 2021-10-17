package edu.boun.edgecloudsim.application.jcci;

public class CustomNetworkTopology {
	//private int n;
	private int n;
	private int maps[][];
	private int xyPos[][];
	
	public CustomNetworkTopology() {
		//
		this.n = 10;
		maps = new int[n][n];
		xyPos = new int[n][2];
		
		
		
		input(1 ,2, 1); //1
		input(1, 3, 1); //2
		input(2, 5, 1); //3
		input(2, 4, 1); //4
		input(3, 4, 1); //5
		input(3, 7, 1); //6
		input(5, 6, 1); //7
		input(4, 6, 1); //8
		input(7, 6, 1); //9
		input(5, 8, 1); //10
		input(7, 9, 1); //11
		input(8, 3, 1); //12
		input(6, 1, 1); 
		input(9, 4, 1);
		input(10,5,1);
		input(10,7,1);
		input(10,8,1);
		
		
	}
	
	private void input(int i, int j, int w) {
		maps[i-1][j-1] = w;
		maps[j-1][i-1] = w;
	}
	
	public void update(int i, int j, int w) {
		maps[i-1][j-1] = w;
		maps[j-1][i-1] = w;
	}
	

	
	
	
	
	
	public int[] dijkstra(int s, int d) {
		int distance[] = new int[n+1];
		boolean[] check = new boolean[n+1];
		int[] route = new int[n+1];
		
		for(int i=0; i < n; i++) {
			distance[i] = Integer.MAX_VALUE;
		}
		
		for( int i = 0; i < n; i++) {
			if(!check[i] && maps[s][i] != 0) {
				distance[i] = maps[s][i];
			}
		}
		
		for(int i = 0; i < n-1; i++) {
			int min = Integer.MAX_VALUE;
			int min_index = -1;
			
			for(int j = 0; j < n; j++) {
				if(!check[j] && distance[j] != Integer.MAX_VALUE) {
					if(distance[j] < min) {
						min = distance[j];
						min_index = j;
					}
				}
			}
			
			check[min_index] = true;
			
			for(int j = 0; j < n; j++) {
				if(!check[j] && maps[min_index][j] != 0) {
					if(distance[j] > distance[min_index] + maps[min_index][j]) {
						distance[j] = distance[min_index]+maps[min_index][j];
						route[j] = min_index+1;
					}
				}
			}
		}
		
		
		
		return route;
	}
}
