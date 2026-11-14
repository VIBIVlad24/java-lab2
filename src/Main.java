package src;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Database db = new Database();
        Scanner sc = new Scanner(System.in);

        System.out.println("=== Мини-СУБД ===");
        System.out.println("EXIT - выход, HELP - помощь");

        while (true) {
            System.out.print("\nSQL> ");
            String input = sc.nextLine().trim();

            if (input.equalsIgnoreCase("EXIT")) {
                db.save();
                System.out.println("Данные сохранены.");
                break;
            }
            if (input.equalsIgnoreCase("HELP")) {
                System.out.println("INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE, '2023-01-01')");
                System.out.println("INSERT INTO cars (id, brand) VALUES (2, 'BMW')");
                System.out.println("SELECT * FROM cars WHERE price > 20000");
                System.out.println("UPDATE cars SET price = 30000 WHERE id = 2");
                System.out.println("DELETE FROM cars WHERE brand = 'Toyota'");
                System.out.println("CREATE INDEX idx_brand ON cars (brand)");
                continue;
            }
            if (input.isEmpty()) continue;

            try {
                Parser.execute(input, db);
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
        sc.close();
    }
}