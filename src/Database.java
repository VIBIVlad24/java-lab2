package src;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Главный класс СУБД. Управляет таблицами, сохранением на диск и загрузкой.
 */

public class Database implements Serializable {
    private Map<String, Table> tables = new HashMap<>();

    public Database() {
        if (!load()) {
            tables.put("cars", new Table("cars", Arrays.asList("id", "brand", "price", "is_electric", "reg_date")));
            tables.put("owners", new Table("owners", Arrays.asList("id", "car_id", "name", "reg_date")));
        }
    }

    public Table getTable(String name) {
        return tables.get(name);
    }

    public void addTable(String name, List<String> columns) {
        tables.put(name, new Table(name, columns));
    }

    public void dropTable(String name) {
        tables.remove(name);
    }

    public void save() {
        Path tmp = Paths.get("db.tmp");
        Path target = Paths.get("db.dat");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmp.toFile()))) {
            oos.writeObject(this);
        } catch (IOException e) {
            System.out.println("Ошибка сохранения: " + e.getMessage());
            return;
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                System.out.println("Ошибка замены файла: " + ex.getMessage());
            }
        }
    }

    private boolean load() {
        File file = new File("db.dat");
        if (!file.exists()) return false;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Database loaded = (Database) ois.readObject();
            this.tables = loaded.tables;
            return true;
        } catch (Exception e) {
            System.out.println("БД повреждена, создаем новую.");
            return false;
        }
    }

    public static class Table implements Serializable {
        String name;
        List<String> columns;
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Map<Object, List<Integer>>> indexes = new HashMap<>();

        public Table(String name, List<String> columns) {
            this.name = name;
            this.columns = columns;
        }

        public boolean insert(Map<String, Object> row) {
            if (row.containsKey("id")) {
                Object newId = row.get("id");
                for (Map<String, Object> r : rows) {
                    if (r.get("id") != null && r.get("id").equals(newId)) return false;
                }
            }
            rows.add(row);
            rebuildIndexes();
            return true;
        }

        public void createIndex(String column) {
            if (!columns.contains(column)) return;
            indexes.put(column, new HashMap<>());
            rebuildIndexes();
            System.out.println("Индекс по " + column + " создан.");
        }

        /**
         * Пересобирает все существующие индексы.
         * Вызывается после вставки, удаления или обновления строк.
         */
        public void rebuildIndexes() {
            for (String col : indexes.keySet()) {
                Map<Object, List<Integer>> idx = new HashMap<>();
                for (int i = 0; i < rows.size(); i++) {
                    Object val = rows.get(i).get(col);
                    if (val != null) {
                        idx.putIfAbsent(val, new ArrayList<>());
                        idx.get(val).add(i);
                    }
                }
                indexes.put(col, idx);
            }
        }
    }
}