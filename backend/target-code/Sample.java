import java.util.*;

public class Sample {
    public static void main(String[] args) {
        int[] numbers = {5, 3, 8, 1};
        int sum = 0;
        for (int i = 0; i < numbers.length; i++) {
            sum += numbers[i];
        }
        System.out.println("Sum: " + sum);

        int result = factorial(4);
        System.out.println("Factorial: " + result);
    }

    static int factorial(int n) {
        if (n <= 1) {
            return 1;
        }
        return n * factorial(n - 1);
    }
}
