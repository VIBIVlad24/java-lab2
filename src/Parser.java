package src;

import java.util.*;
import java.util.regex.*;

public class Parser {

    private static final Pattern INSERT_PATTERN = Pattern.compile("INSERT INTO (\\w+)\\s*(?:\\((.+?)\\))?\\s*VALUES\\s*\\((.+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_PATTERN = Pattern.compile("SELECT (.+) FROM (\\w+)(?: WHERE (.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN = Pattern.compile("UPDATE (\\w+) SET (.+) WHERE (.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_PATTERN = Pattern.compile("DELETE FROM (\\w+)(?: WHERE (.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_IDX_PATTERN = Pattern.compile("CREATE INDEX \\w+ ON (\\w+) \\((\\w+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile("CREATE TABLE (\\w+) \\((.+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile("DROP TABLE (\\w+)", Pattern.CASE_INSENSITIVE);

    public static void execute(String sql, Database db) {
        sql = sql.trim();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);
        Matcher m;

        if ((m = CREATE_TABLE_PATTERN.matcher(sql)).matches()) {
            String tableName = m.group(1);
            String[] cols = m.group(2).split(",");
            List<String> colList = new ArrayList<>();
            for (String c : cols) colList.add(c.trim().split(" ")[0]);
            db.addTable(tableName, colList);
            System.out.println("Таблица " + tableName + " создана.");
            return;
        }

        if ((m = DROP_TABLE_PATTERN.matcher(sql)).matches()) {
            db.dropTable(m.group(1));
            System.out.println("Таблица удалена.");
            return;
        }

        if ((m = CREATE_IDX_PATTERN.matcher(sql)).matches()) {
            Database.Table t = db.getTable(m.group(1));
            if (t != null) t.createIndex(m.group(2));
            return;
        }

        if ((m = INSERT_PATTERN.matcher(sql)).matches()) {
            String tableName = m.group(1);
            String colsStr = m.group(2);
            String valsStr = m.group(3);

            Database.Table table = db.getTable(tableName);
            if (table == null) { System.out.println("Нет таблицы"); return; }

            String[] vals = splitValues(valsStr);
            Map<String, Object> row = new LinkedHashMap<>();

            if (colsStr != null) {
                String[] cols = colsStr.split(",");
                for (int i = 0; i < cols.length; i++) {
                    row.put(cols[i].trim(), parseValue(vals[i].trim()));
                }
            } else {
                for (int i = 0; i < vals.length && i < table.columns.size(); i++) {
                    row.put(table.columns.get(i), parseValue(vals[i].trim()));
                }
            }

            if (table.insert(row)) System.out.println("Добавлено.");
            else System.out.println("Ошибка: дубликат id.");
            return;
        }

        if ((m = UPDATE_PATTERN.matcher(sql)).matches()) {
            String tableName = m.group(1);
            String setStr = m.group(2);
            String whereStr = m.group(3);
            Database.Table table = db.getTable(tableName);
            if (table == null) { System.out.println("Нет таблицы"); return; }

            String[] sets = setStr.split(",");
            int count = 0;
            for (Map<String, Object> row : table.rows) {
                if (checkCondition(row, whereStr)) {
                    for (String s : sets) {
                        String[] kv = s.split("=");
                        row.put(kv[0].trim(), parseValue(kv[1].trim()));
                    }
                    count++;
                }
            }
            System.out.println("Обновлено строк: " + count);
            return;
        }

        if ((m = DELETE_PATTERN.matcher(sql)).matches()) {
            String tableName = m.group(1);
            String whereStr = m.group(2);
            Database.Table table = db.getTable(tableName);
            if (table == null) { System.out.println("Нет таблицы"); return; }

            Iterator<Map<String, Object>> it = table.rows.iterator();
            int count = 0;
            while (it.hasNext()) {
                Map<String, Object> row = it.next();
                if (whereStr == null || checkCondition(row, whereStr)) {
                    it.remove();
                    count++;
                }
            }
            System.out.println("Удалено строк: " + count);
            return;
        }

        if ((m = SELECT_PATTERN.matcher(sql)).matches()) {
            String cols = m.group(1);
            String tableName = m.group(2);
            String where = m.group(3);

            Database.Table table = db.getTable(tableName);
            if (table == null) { System.out.println("Нет таблицы"); return; }

            List<Map<String, Object>> result = new ArrayList<>();

            if (where != null && where.contains("=") && !where.contains(">") && !where.contains("<")) {
                String[] parts = where.split("=");
                String col = parts[0].trim();
                if (table.indexes.containsKey(col)) {
                    Object val = parseValue(parts[1].trim());
                    List<Integer> idxList = table.indexes.get(col).get(val);
                    if (idxList != null) {
                        for (int i : idxList) result.add(table.rows.get(i));
                    }
                    printResult(result, cols, table.columns);
                    return;
                }
            }

            for (Map<String, Object> row : table.rows) {
                if (where == null || checkCondition(row, where)) {
                    result.add(row);
                }
            }
            printResult(result, cols, table.columns);
            return;
        }

        System.out.println("Синтаксическая ошибка.");
    }

    private static String[] splitValues(String s) {
        List<String> res = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder cur = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '\'') inQuotes = !inQuotes;
            if (c == ',' && !inQuotes) {
                res.add(cur.toString()); cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        res.add(cur.toString());
        return res.toArray(new String[0]);
    }

    private static Object parseValue(String val) {
        val = val.replace("'", "").trim();
        if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false")) return Boolean.parseBoolean(val);
        if (val.matches("\\d{4}-\\d{2}-\\d{2}")) return val;
        try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(val); } catch (NumberFormatException ignored) {}
        return val;
    }

    private static boolean checkCondition(Map<String, Object> row, String where) {
        where = where.trim();
        String op = "=";
        int opIdx = -1;

        if (where.contains(">=")) { op = ">="; opIdx = where.indexOf(">="); }
        else if (where.contains("<=")) { op = "<="; opIdx = where.indexOf("<="); }
        else if (where.contains("!=")) { op = "!="; opIdx = where.indexOf("!="); }
        else if (where.contains(">")) { op = ">"; opIdx = where.indexOf(">"); }
        else if (where.contains("<")) { op = "<"; opIdx = where.indexOf("<"); }
        else if (where.contains("=")) { op = "="; opIdx = where.indexOf("="); }

        if (opIdx == -1) return false;

        String col = where.substring(0, opIdx).trim();
        Object val = parseValue(where.substring(opIdx + op.length()).trim());
        Object rowVal = row.get(col);

        if (rowVal == null) return false;

        if (rowVal instanceof Number && val instanceof Number) {
            double a = ((Number) rowVal).doubleValue();
            double b = ((Number) val).doubleValue();
            switch (op) {
                case "=": return a == b;
                case ">": return a > b;
                case "<": return a < b;
                case ">=": return a >= b;
                case "<=": return a <= b;
                case "!=": return a != b;
            }
        }
        return rowVal.toString().equals(val.toString()) && op.equals("=");
    }

    private static void printResult(List<Map<String, Object>> result, String cols, List<String> tableCols) {
        if (result.isEmpty()) { System.out.println("Пустой результат."); return; }
        List<String> printCols = cols.equals("*") ? tableCols : Arrays.asList(cols.split(","));

        for (String c : printCols) System.out.printf("%-15s", c.trim().toUpperCase());
        System.out.println();
        for (int i = 0; i < printCols.size() * 15; i++) System.out.print("-");
        System.out.println();

        for (Map<String, Object> row : result) {
            for (String c : printCols) System.out.printf("%-15s", row.get(c.trim()));
            System.out.println();
        }
    }
}