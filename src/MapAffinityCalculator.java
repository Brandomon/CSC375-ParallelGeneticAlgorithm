public class MapAffinityCalculator {

    public static double calculateAffinity(int[][] map) {
        int rows = map.length;
        int cols = map[0].length;

        double affinity = 0.0;

        // Define the weights for different scenarios
        double weightSameAdjacent = -0.5;  		// Less affinity for two of the same number next to each other
        double weightDifferentAdjacent = 2;		// More affinity for a 1 closer to a 2

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int current = map[i][j];

                // Check the neighboring cells (up, down, left, right)
                if (i > 0 && map[i - 1][j] == current) {
                    affinity += weightSameAdjacent;
                }
                if (i < rows - 1 && map[i + 1][j] == current) {
                    affinity += weightSameAdjacent;
                }
                if (j > 0 && map[i][j - 1] == current) {
                    affinity += weightSameAdjacent;
                }
                if (j < cols - 1 && map[i][j + 1] == current) {
                    affinity += weightSameAdjacent;
                }

                // Check diagonally adjacent cells
                if (i > 0 && j > 0 && map[i - 1][j - 1] == current) {
                    affinity += weightSameAdjacent;
                }
                if (i > 0 && j < cols - 1 && map[i - 1][j + 1] == current) {
                    affinity += weightSameAdjacent;
                }
                if (i < rows - 1 && j > 0 && map[i + 1][j - 1] == current) {
                    affinity += weightSameAdjacent;
                }
                if (i < rows - 1 && j < cols - 1 && map[i + 1][j + 1] == current) {
                    affinity += weightSameAdjacent;
                }

                // Check for 1 closer to 2
                if (current == 1) {
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            int ni = i + x;
                            int nj = j + y;
                            if (ni >= 0 && ni < rows && nj >= 0 && nj < cols && map[ni][nj] == 2) {
                                affinity += weightDifferentAdjacent;
                            }
                        }
                    }
                }
            }
        }
        return affinity;
    }

    public static void main(String[] args) {
        int[][] map = {
            {0, 1, 2, 2, 0},
            {1, 2, 2, 1, 1},
            {2, 0, 1, 1, 0},
            {0, 1, 2, 2, 1},
            {1, 1, 0, 0, 2}
        };

        double affinity = calculateAffinity(map);
        System.out.println("Affinity: " + affinity);
    }
}