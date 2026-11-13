import java.io.*;
import java.util.*;

/**
 * Главный класс базы данных. Управляет таблицами и сериализацией.
 */
public class Database implements Serializable {
    private static final long serialVersionUID = 1L;
    private Map<String, Table> tables = new HashMap<>();

    public Database() {
        // Создаем таблицы по умолчанию при первом запуске
        if (!loadFromDisk()) {
            tables.put("cars", new Table("cars", Arrays.asList("id", "brand", "price", "is_electric")));
            tables.put("owners", new Table("owners", Arrays.asList("id", "car_id", "name", "reg_date")));
        }
    }

    public Table getTable(String name) {
        return tables.get(name);
    }

    public void saveToDisk() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("db.dat"))) {
            oos.writeObject(this);
        } catch (IOException e) {
            System.out.println("Ошибка сохранения БД: " + e.getMessage());
        }
    }

    private boolean loadFromDisk() {
        File file = new File("db.dat");
        if (!file.exists()) return false;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Database loaded = (Database) ois.readObject();
            this.tables = loaded.tables;
            return true;
        } catch (Exception e) {
            System.out.println("Ошибка загрузки БД, создаем новую.");
            return false;
        }
    }

    /**
     * Внутренний класс таблицы
     */
    public static class Table implements Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        List<String> columns;
        List<Map<String, Object>> rows = new ArrayList<>();
        
        // Индексы: имя_колонки -> (значение -> список индексов строк)
        Map<String, Map<Object, List<Integer>>> indexes = new HashMap<>();

        public Table(String name, List<String> columns) {
            this.name = name;
            this.columns = columns;
        }

        public boolean insert(Map<String, Object> row) {
            // Проверка уникальности первичного ключа (id)
            if (row.containsKey("id")) {
                Object newId = row.get("id");
                for (Map<String, Object> r : rows) {
                    if (r.get("id").equals(newId)) return false; // Дубликат
                }
            }
            rows.add(row);
            updateIndexes(rows.size() - 1, row);
            return true;
        }

        private void updateIndexes(int rowIndex, Map<String, Object> row) {
            for (String col : indexes.keySet()) {
                if (row.containsKey(col)) {
                    Object val = row.get(col);
                    indexes.get(col).putIfAbsent(val, new ArrayList<>());
                    indexes.get(col).get(val).add(rowIndex);
                }
            }
        }

        public void createIndex(String column) {
            if (!columns.contains(column)) return;
            Map<Object, List<Integer>> index = new HashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                Object val = rows.get(i).get(column);
                if (val != null) {
                    index.putIfAbsent(val, new ArrayList<>());
                    index.get(val).add(i);
                }
            }
            indexes.put(column, index);
            System.out.println("Индекс по колонке " + column + " успешно создан.");
        }
    }
}