import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Database db = new Database();
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Мини-СУБД v1.0 ===");
        System.out.println("Введите 'EXIT' для выхода. Введите 'HELP' для подсказки.");

        while (true) {
            System.out.print("\nSQL> ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("EXIT")) {
                db.saveToDisk();
                System.out.println("Данные сохранены. До свидания!");
                break;
            }
            if (input.equalsIgnoreCase("HELP")) {
                System.out.println("Примеры запросов:");
                System.out.println("INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE)");
                System.out.println("SELECT * FROM cars");
                System.out.println("CREATE INDEX idx_cars_brand ON cars (brand)");
                continue;
            }
            if (input.isEmpty()) continue;

            try {
                Parser.execute(input, db);
            } catch (Exception e) {
                System.out.println("Ошибка выполнения: " + e.getMessage());
            }
        }
        scanner.close();
    }
}