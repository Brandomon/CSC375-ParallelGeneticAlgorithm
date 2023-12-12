import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MapGUI extends JFrame implements ActionListener {

    private int[][] mapData;				// 2-dimensional integer array depicting a map layout
    private int currentIndex = 0;			// Current index within the pool of map outputs
    private static final int SLEEP = 500;	// Delay between map drawings in milliseconds


    public MapGUI(int[][] mapData) {
        this.mapData = mapData;
        setTitle("Map Display");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        int numRows = mapData.length;
        int numCols = mapData[0].length;
        int cellSize = 40; // Size of each cell
        int borderWidth = 16; // Border width

        // Calculate the width and height of the map area
        int mapWidth = numCols * cellSize;
        int mapHeight = numRows * cellSize;

        // Calculate the JFrame dimensions to include the map and border
        int width = mapWidth + 8 * borderWidth;
        int height = mapHeight + 8 * borderWidth;

        setSize(width, height);
        setLocationRelativeTo(null); // Centers GUI on screen
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (currentIndex < mapData.length) {
            currentIndex++;
        } else {
            return;
        }
    }
    
    @Override
    public void paint(Graphics g) {
        super.paint(g);						// Paints the container
        int cellSize = 40;					// Size of each cell
        int numRows = mapData.length;		// Number of rows within the 2-dimensional integer array
        int numCols = mapData[0].length;	// Number of columns within the 2-dimensional integer array
        int borderWidth = 16;				// Border width

        // Calculate the starting coordinates to center the map
        int startX = borderWidth + (getWidth() - numCols * cellSize - 2 * borderWidth) / 2;		// Starting centered x-coordinate of screen
        int startY = borderWidth + (getHeight() - numRows * cellSize - 2 * borderWidth) / 2;	// Starting centered y-coordinate of screen
        int circleSize = cellSize / 2; 															// Size of the blue circles depicting type2-stations

        // For each row within the array
        for (int row = 0; row < numRows; row++) {
        	
        	// For each column within each row
            for (int col = 0; col < numCols; col++) {
            	
            	// Set cell value to current row and column station type value
                int cellValue = mapData[row][col];
                
                // Set the default color for cells to white
                Color cellColor = Color.WHITE;

                // If cell is type1 (Red Square)
                if (cellValue == 1) {                	
                	// Turn cell color to red
                    cellColor = Color.RED;
                    
                    // Set graphics color to cellColor
                    g.setColor(cellColor);
                    
                    // Adjust square position to center of cell
                    int squareX = startX + col * cellSize + cellSize / 2 - circleSize / 2;
                    int squareY = startY + row * cellSize + cellSize / 2 - circleSize / 2;
                    
                    // Fill the graphics rectangle
                    g.fillRect(squareX, squareY, circleSize, circleSize);
                    
                } else if (cellValue == 2) {
                	// Set background to white
                    cellColor = Color.WHITE;
                    g.setColor(cellColor);
                    g.fillRect(startX + col * cellSize, startY + row * cellSize, cellSize, cellSize);
                    
                    // Set circle color
                    cellColor = Color.BLUE;
                    g.setColor(cellColor);
                    
                    // Set circle position to center of cell
                    int circleX = startX + col * cellSize + cellSize / 2 - circleSize / 2;
                    int circleY = startY + row * cellSize + cellSize / 2 - circleSize / 2;
                    
                    // Fill the graphics circle
                    g.fillOval(circleX, circleY, circleSize, circleSize);
                }
                // Set border color to black
                g.setColor(Color.BLACK);
                
                // Draw border rectangles
                g.drawRect(startX + col * cellSize, startY + row * cellSize, cellSize, cellSize);
            }
        }
    }

    public void updateMapData(int[][] newData) {
    	
    	// Gather updated map data
        mapData = newData;
        
        // Repaint the GUI with the updated map data
        repaint();
    }
    
    public static void main(String[] args) {
    	
    	// Create blocking queue to pool map data
        BlockingQueue<int[][]> mapDataPool = new LinkedBlockingQueue<>();
        
        // Generate random map data
        int[][] mapData1 = ParallelGeneticAlgorithm.generateRandomFloorMap();
        int[][] mapData2 = ParallelGeneticAlgorithm.generateRandomFloorMap();
        int[][] mapData3 = ParallelGeneticAlgorithm.generateRandomFloorMap();
        int[][] mapData4 = ParallelGeneticAlgorithm.generateRandomFloorMap();
        int[][] mapData5 = ParallelGeneticAlgorithm.generateRandomFloorMap();
        int[][] mapData6 = ParallelGeneticAlgorithm.generateRandomFloorMap();
        
        // Add map data to pool
        mapDataPool.add(mapData1);
        mapDataPool.add(mapData2);
        mapDataPool.add(mapData3);
        mapDataPool.add(mapData4);
        mapDataPool.add(mapData5);
        mapDataPool.add(mapData6);

        // Display each map from pool on GUI for DELAY milliseconds
        SwingUtilities.invokeLater(() -> {
            MapGUI mapGUI = new MapGUI(mapDataPool.poll());

            // Create a separate thread to manage the queue and update the GUI
            Thread updaterThread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(SLEEP);
                        int[][] nextMapData = mapDataPool.poll();
                        if (nextMapData != null) {
                            mapGUI.updateMapData(nextMapData);
                        } else {
                            break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            // Start running the updater thread
            updaterThread.start();
        });
    }
}