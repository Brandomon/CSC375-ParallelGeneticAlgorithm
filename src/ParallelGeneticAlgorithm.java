import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Exchanger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.SwingUtilities;

//-----------------------------------------------------------------------------------------------------------------------------------------------------------------
//
//	Written by: Brandon LaPointe
//	Class: CSC375 Parallel Computing
//	Professor: Doug Lea
//	Date: 09/29/2023
//
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------
// NOTES:
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------
//
// There are N stations (N at least 48) and M (M at least N) spots to place them on two-dimensional space (of any shape you like) representing a one-floor factory.
// (There may be unoccupied spots serving as "holes".)
// The N stations come in F (at least 2) types representing their function.
// The different types may have different shapes, occupying multiple adjacent spots.
// 
// There is a metric representing the benefit (affinity) of placing any two stations A and B near each other based on their Function and distance,
// with possibly different maximum values based on capacity or rate.
// The goal is to maximize total affinity.
// 
// Each of K parallel tasks solve by (at least in part randomly) swapping or modifying station spots (possibly with holes),
// and occasionally exchanging parts of solutions with others. (This is the main concurrent coordination problem.)
// Run the program on a computer with at least 32 cores (and K at least 32). (You can develop with smaller K.)
//
// The program occasionally (for example twice per second) graphically displays solutions until converged or performs a given number of iterations.
// Details are up to you.
//
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------
//
//					ROUGH IDEA VISUAL:
//
//	TASK1		TASK2		TASK3		TASK4		...
//	|			|			|			|
//	Generate randomized map utilizing ThreadLocalRandom
//	Calculate affinity of map using direct neighbors
//	Possibly mutate utilizing ThreadLocalRandom
//	-Calculate affinity of mutated map
//	--Add map to pool
//	|			|			|			|
//	--------------------SYNCHRONIZE--------------------
//	|			|			|			|
//	Exchange map halves with next thread
//	Validate child map
//	-If valid, calculate affinity of child map
//	--Add map to pool
//	Output map to pool to be displayed
//	Display maps for 500ms each on GUI
//
//-----------------------------------------------------------------------------------------------------------------------------------------------------------------
// 
// For randomization, use ThreadLocalRandom: A random number generator isolated to the current thread.
//
// For exchanging partial data, use exchange(V x, long timeout, TimeUnit unit): Waits for another thread to arrive at this exchange point (unless the current thread
// is interrupted or the specified waiting time elapses), and then transfers the given object to it, receiving its object in return.
//
// For synchronization, use Lock, CountDownLatch, CyclicBarrier, or Phaser depending on the synchronization needed.
// 
// -----------------------------------------------------------------------------------------------------------------------------------------------------------------
public class ParallelGeneticAlgorithm implements Runnable {
	
    private final static int ROWS = 8;							// Number of rows within floor plan grid / integer array representation
    private final static int COLUMNS = 8;						// Number of columns within floor plan grid / integer array representation
    private final static int STATIONS = 48;						// Number of stations required to be a valid floor plan
    private final static int TYPES = 3;							// Types of stations, including empty.
    private final static int ITERATIONS = 32;					// Number of iterations for each parallel task to run through (32-100)
    private final static int MUTATION_RATE = 10;				// Mutation rate of each randomly generated map (1 in MUTATION_RATE chance of mutation.)
    private final static double SAME_ADJACENT_WEIGHT = -0.5;	// Negative affinity of same stations near each other
    private final static double DIFFERENT_ADJACENT_WEIGHT = 2;	// Positive affinity of different stations near each other
    private final static int SLEEP = 500;						// Length of time in milliseconds for thread sleep (delay) between each repaint of GUI
    
    // Lock used for synchronization of threads
    private static Lock lock = new ReentrantLock();
    
    // Blocking queue used to pool all outputs of mapData before drawing to GUI
    private static BlockingQueue<int[][]> mapDataPool = new LinkedBlockingQueue<>();
    
    // Exchanger used to swap partial solutions with between threads
    private final static Exchanger<int[][]> mapExchange = new Exchanger<>();
    
    // Detect the number of CPU cores (threads) available
    private static int numCores = Runtime.getRuntime().availableProcessors();
    
    // Initialize CyclicBarrier of size numCores
    private final static CyclicBarrier barrier = new CyclicBarrier(numCores);
	
    //----------------------------------------------------------------------
    // generateRandomFloorMap
    //----------------------------------------------------------------------
    // Creates a random 2-dimensional integer array of size ROWS by COLUMNS
    // containing either 0 (empty), 1 (station type1), or 2 (station type2).
    //
    
    public static int[][] generateRandomFloorMap() {
        int[][] array = new int[ROWS][COLUMNS];
        int stationCount = 0;
            while (stationCount < STATIONS) {
                for (int i = 0; i < ROWS; i++) {
                    for (int j = 0; j < COLUMNS; j++) {
                        if (array[i][j] == 0 && stationCount < STATIONS) {
                            int randomValue = ThreadLocalRandom.current().nextInt(TYPES);
                            array[i][j] = randomValue;
                            if (randomValue == 1 || randomValue == 2) {
                                stationCount++;
                            }
                        }
                    }
                }
            }
        return array;
    }    
    
    //----------------------------------------------------------------------
    // calculateAffinity
    //----------------------------------------------------------------------
    // Calculates the affinity as a double for a given int[][] floor map by
    // giving positive affinity for values of different adjacent values and
    // giving negative affinity for similar adjacent values.
    // 
    
    public static double calculateAffinity(int[][] map) {
        int rows = map.length;
        int cols = map[0].length;

        double affinity = 0.0;

        // For each row within the array
        for (int i = 0; i < rows; i++) {
        	
        	// For each column within each row
            for (int j = 0; j < cols; j++) {
            	
            	// Set current to map at index [i][j]
                int current = map[i][j];
                
                // Check the upper neighboring cell for same number adjacency
                if (i > 0 && map[i - 1][j] == current) {
                    affinity += SAME_ADJACENT_WEIGHT;
                }
                // Check the lower neighboring cell for same number adjacency
                if (i < rows - 1 && map[i + 1][j] == current) {
                    affinity += SAME_ADJACENT_WEIGHT;
                }
                // Check the left neighboring cell for same number adjacency
                if (j > 0 && map[i][j - 1] == current) {
                    affinity += SAME_ADJACENT_WEIGHT;
                }
                // Check the right neighboring cell for same number adjacency
                if (j < cols - 1 && map[i][j + 1] == current) {
                    affinity += SAME_ADJACENT_WEIGHT;
                }

                // Check upper left diagonally adjacent cell for same number adjacency
                if (i > 0 && j > 0 && map[i - 1][j - 1] == current) {
                    affinity += SAME_ADJACENT_WEIGHT;
                }
                // Check upper right diagonally adjacent cell for same number adjacency
                if (i > 0 && j < cols - 1 && map[i - 1][j + 1] == current) {
                    affinity += SAME_ADJACENT_WEIGHT;
                }
                // Check lower left diagonally adjacent cell for same number adjacency
                if (i < rows - 1 && j > 0 && map[i + 1][j - 1] == current) {
                    affinity += SAME_ADJACENT_WEIGHT;
                }
                // Check lower right diagonally adjacent cell for same number adjacency
                if (i < rows - 1 && j < cols - 1 && map[i + 1][j + 1] == current) {
                    affinity += SAME_ADJACENT_WEIGHT;
                }

                // Check for type1 close to type2
                if (current == 1) {
                	// for each cell around the current
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            int ni = i + x;
                            int nj = j + y;
                            // Check if the neighboring cell is within bounds and contains a 2
                            if (ni >= 0 && ni < rows && nj >= 0 && nj < cols && map[ni][nj] == 2) {
                                affinity += DIFFERENT_ADJACENT_WEIGHT;
                            }
                        }
                    }
                }
            }
        }
        return affinity;
    }
    
    //----------------------------------------------------------------------
    // mutate
    //----------------------------------------------------------------------
    // Performs a genetic algorithm operation to mutate an existing map by
    // randomly selecting one 2-dimensional integer array element and changing
    // it to a 1 or 2 randomly if it is a 0, or swapping between 1 and 2.
    //
    
    private static int[][] mutate(int[][] array) {
    	
    	// Gather row and column lengths from incoming array
    	int rows = array.length;
    	int columns = array[0].length;
    	
    	// Select random row and column from array to mutate
    	int mutationRow = ThreadLocalRandom.current().nextInt(rows);
    	int mutationColumn = ThreadLocalRandom.current().nextInt(columns);
    	
    	// Mutate selected array element
    	if (array[mutationRow][mutationColumn] == 0) {
    		array[mutationRow][mutationColumn] = ThreadLocalRandom.current().nextInt(2) + 1;
    	}
    	else if (array[mutationRow][mutationColumn] == 1) {
    		array[mutationRow][mutationColumn] = 2;
    	}
    	else if (array[mutationRow][mutationColumn] == 2) {
    		array[mutationRow][mutationColumn] = 1;
    	}
		return array;	
    }
    
    
    //----------------------------------------------------------------------
    // crossover
    //----------------------------------------------------------------------
    // Performs a genetic algorithm operation to create a new map by
    // combining two parent maps.
    //
    
    public int[][] crossover(int[][] parent1, int[][] parent2) {
    	
    	// Initialize new Map childMap as a clone of parent1
        int[][] childMap = new int[parent1.length][parent1[0].length];
        
        for (int i = 0; i < parent1.length / 2; i++) {
            for (int j = 0; j < parent1[0].length; j++) {
                childMap[i][j] = parent1[i][j];
            }
        }
        
        // Use second (bottom) half of parent2
        for (int i = childMap.length / 2; i < childMap.length; i++) {
            for (int j = 0; j < childMap[0].length; j++) {
                childMap[i][j] = parent2[i][j];                
            }
        }
        // Validate child map
        int childStations = 0;
        for (int i = 0; i < childMap.length; i++) {
            for (int j = 0; j < childMap[0].length; j++) {
                if (childMap[i][j] == 1 || childMap[i][j] == 2) {
                	childStations++;
                }
            }
        }
        // If child map has required number of stations
    	if (childStations == STATIONS) {
    		// Return valid child map
    		return childMap;
    	} else {
    		// If child map isn't valid, return null
    		return null;
    	}
    }
    

    //----------------------------------------------------------------------
    // printArray
    //----------------------------------------------------------------------
    // Prints a 2-dimensional integer array to console.
    //
    
    private static void printArray(int[][] array) {
        for (int[] row : array) {
            for (int value : row) {
                System.out.print(value + " ");
            }
            System.out.println();
        }
    }
    
    
    //----------------------------------------------------------------------
    // run
    //----------------------------------------------------------------------
    // Runs the floor map genetic algorithm on a single thread to output
    // multiple iterations of maps to the blocking queue called mapDataPool.
    // Each generated map has a chance to have one random station mutated before
    // the map data of the thread is exchanged with another thread and halved,
    // piecing the top half of the thread's original or mutated map (if mutation
    // has occurred) and the bottom half of the received map from the exchanger.
    // All maps generated, if valid (Number of stations == STATIONS), will be
    // added into the blocking queue to be painted onto the GUI one at a time
    // for SLEEP milliseconds each.
    //
    
    public void run() {
		// Generate and print random arrays for the specified number of iterations
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
        	
	    	// Generate randomized integer array
	    	int[][] randomArray = generateRandomFloorMap();
            
	        // Randomize mutationChance with an integer between 0 and 10 (inclusive) (1/10 chance of mutation)
	        int mutationGene = ThreadLocalRandom.current().nextInt(MUTATION_RATE);
	        
        	// Calculate affinity of array
            double affinity = calculateAffinity(randomArray);
            
            // Add initial floor map to mapDataPool
            mapDataPool.add(randomArray);
               
	        try {
	        	// Lock the thread synchronization lock for synchronized output from each thread
	        	lock.lock();	         
	        	
	        	// Print out current thread and iteration
	            System.out.print(Thread.currentThread().getName() + " - Iteration " + (iteration + 1) + " : Affinity: " + affinity);
	            
	        	// If mutationGene is zero, mutate the array
	            if (mutationGene == 0) {
	            	
	            	// Mutate the array
	                randomArray = mutate(randomArray);
	                
	                // Recalculate affinity of the array
	                affinity = calculateAffinity(randomArray);
	                
	                // Add mutated floor map to mapDataPool
	                mapDataPool.add(randomArray);
	                
	                // Display on the console that a mutation occurred and the new affinity value
	                System.out.print(" <<< Mutation Occurred! Post-Mutation Affinity : " + affinity);
	        	}
	            
	            // New line to separate outputs
	            System.out.println();
	            
	        } finally {
	        	// Unlock the thread synchronization lock
	        	lock.unlock();
	        }

			try {
				// Wait for all threads to arrive before exchanging map data
				barrier.await();
				
				// Exchange map data between threads
				int[][] exchangeMap = mapExchange.exchange(randomArray);
				
				// Use crossover to produce child map from both parent maps
				int[][] childMap = crossover(randomArray, exchangeMap);
				
				try {
					// Lock the thread synchronization lock for synchronized output
					lock.lock();
					
					// If child map is valid
					if (childMap != null) {
						// Calculate affinity of child map
						double childAffinity = calculateAffinity(childMap);
						double parent2Affinity = calculateAffinity(exchangeMap);
						// Display crossover message and affinity onto console
						System.out.println("Crossover : Parent1 Affinity: " + affinity + " - Parent2 Affinity: " + parent2Affinity + " - Child Affinity: " + childAffinity);
						
						// Add child floor map data to mapDataPool
						mapDataPool.add(childMap);
					}
					// Else child map is not valid
					else {
						// Display that child map is not valid on the console
						//System.out.println("Child map not valid");
					}
				} finally {
					// Unlock the thread synchronization lock
					lock.unlock();
				}
				// Await for all threads to reach this point for synchronized crossover output
				barrier.await();
				
			} catch (InterruptedException | BrokenBarrierException e) {
				e.printStackTrace();
			}		
        }            
    }
    
    //----------------------------------------------------------------------
    // main
    //----------------------------------------------------------------------
    // Runs the floor map genetic algorithm across multiple threads in
    // parallel where the number of threads is equal on the number of
    // available cores within the system before outputting each of the
    // generated maps to a pool where they are drawn to the GUI for
    // SLEEP milliseconds each, closing after the last
    // map within the pool is drawn.
    //
    
	public static void main(String[] args) {
		// Display number of available cores onto console {
        System.out.println("Number of available cores: " + numCores + "\n");

        // Create and start a thread for each core
        for (int core = 0; core < numCores; core++) {
            Thread thread = new Thread(new ParallelGeneticAlgorithm(), "Thread " + (core + 1));
            thread.start();
        }
      
        // Invoke the mapGUI to poll data from the mapDataPool blocking queue  
        SwingUtilities.invokeLater(() -> {
            MapGUI mapGUI = new MapGUI(mapDataPool.poll());

            // Create a separate thread to manage the queue and update the GUI
            Thread updaterThread = new Thread(() -> {
                while (true) {
                    try {
                    	// Sleep for half a second between redrawing new map data on GUI
                        Thread.sleep(SLEEP);
                        
                        // Poll next map data array from mapDataPool blocking queue
                        int[][] nextMapData = mapDataPool.poll();
                        
                        // If next map data is not null
                        if (nextMapData != null) {
                        	
                        	// Update map data
                            mapGUI.updateMapData(nextMapData);
                            
                        // Else next map data is null
                        } else {
                        	
                        	// Stop running the updater thread
                            break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                // Close the GUI and exit the application after last map update
                System.exit(0);
            });
            // Start running updater thread
            updaterThread.start();
        });
    }
}
