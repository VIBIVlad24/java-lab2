import java.util.*;
import java.util.regex.*;

/**
 * Примитивный парсер SQL-подобных запросов.
 */
public class Parser {

    // Регулярные выражения для простых запросов
    private static final Pattern INSERT_PATTERN = Pattern.compile("INSERT INTO (\\w+) VALUES \\((.+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_PATTERN = Pattern.compile("SELECT (.+) FROM (\\w+)(?: WHERE (.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN = Pattern.compile("UPDATE (\\w+) SET (.+) WHERE (.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_PATTERN = Pattern.compile("DELETE FROM (\\w+) WHERE (.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_IDX_PATTERN = Pattern.compile("CREATE INDEX \\w+ ON (\\w+) \\((\\w+)\\)", Pattern.CASE_INSENSITIVE);

    public static void execute(String sql, Database db) {
        sql = sql.trim();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);

        Matcher m;

        // 1. CREATE INDEX
        m = CREATE_IDX_PATTERN.matcher(sql);
        if (m.matches()) {
            String tableName = m.group(1);
            String col = m.group(2);
            Database.Table table = db.getTable(tableName);
            if (table != null) table.createIndex(col);
            return;
        }

        // 2. INSERT
        m = INSERT_PATTERN.matcher(sql);
        if (m.matches()) {
            String tableName = m.group(1);
            String[] values = m.group(2).split(",");
            Database.Table table = db.getTable(tableName);
            if (table == null) { System.out.println("Таблица не найдена"); return; }
            
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < values.length && i < table.columns.size(); i++) {
                row.put(table.columns.get(i), parseValue(values[i].trim()));
            }
            if (table.insert(row)) System.out.println("Строка добавлена.");
            else System.out.println("Ошибка: Дубликат первичного ключа.");
            return;
        }

        // 3. SELECT
        m = SELECT_PATTERN.matcher(sql);
        if (m.matches()) {
            String cols = m.group(1);
            String tableName = m.group(2);
            String where = m.group(3);
            
            Database.Table table = db.getTable(tableName);
            if (table == null) { System.out.println("Таблица не найдена"); return; }

            List<Map<String, Object>> result = new ArrayList<>();
            
            // Оптимизация с индексом
            if (where != null && table.indexes.containsKey(where.split("=")[0].trim())) {
                String[] parts = where.split("=");
                Object val = parseValue(parts[1].trim());
                List<Integer> idxList = table.indexes.get(parts[0].trim()).get(val);
                if (idxList != null) {
                    for (int i : idxList) result.add(table.rows.get(i));
                }
            } else {
                // Обычный перебор
                for (Map<String, Object> row : table.rows) {
                    if (where == null || checkCondition(row, where)) {
                        result.add(row);
                    }
                }
            }
            printResult(result, cols, table.columns);
            return;
        }
        
        System.out.println("Запрос не распознан или пока не поддерживается.");
    }

    private static Object parseValue(String val) {
        val = val.replace("'", "").trim();
        if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false")) return Boolean.parseBoolean(val);
        try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(val); } catch (NumberFormatException ignored) {}
        return val; // Строка
    }

    private static boolean checkCondition(Map<String, Object> row, String where) {
        String[] parts = where.split("=");
        String col = parts[0].trim();
        Object val = parseValue(parts[1].trim());
        return row.get(col) != null && row.get(col).equals(val);
    }

    private static void printResult(List<Map<String, Object>> result, String cols, List<String> tableCols) {
        if (result.isEmpty()) { System.out.println("Пустой результат."); return; }
        
        List<String> printCols = cols.equals("*") ? tableCols : Arrays.asList(cols.split(","));
        
        // Печать заголовка
        for (String c : printCols) System.out.printf("%-15s", c.trim().toUpperCase());
        System.out.println("\n" + "-".repeat(printCols.size() * 15));
        
        // Печать данных
        for (Map<String, Object> row : result) {
            for (String c : printCols) {
                System.out.printf("%-15s", row.get(c.trim()));
            }
            System.out.println();
        }
    }
}