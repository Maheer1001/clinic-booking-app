package database;

import app.DB;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;

/**
 * Prints the live DB schema (tables/columns/PKs/FKs) to test output.
 * This is intentionally a test so it can be run via Maven easily:
 * mvn test -Pdb-tests -Dtest=SchemaIntrospectTest. Not part of -Psetup-check (it checks nothing).
 */
@Tag("db")
@Tag("schema-dump")
public class SchemaIntrospectTest {

    @Test
    void dumpSchema() throws Exception {
        try (Connection c = DB.connect()) {
            DatabaseMetaData meta = c.getMetaData();

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name == null) continue;
                    // Keep our app tables (quoted CamelCase in Postgres, uppercase in H2)
                    if (name.startsWith("pg_") || name.startsWith("sql_")) continue;
                    tables.add(name);
                }
            }
            tables.sort(String.CASE_INSENSITIVE_ORDER);

            System.out.println("=== SCHEMA DUMP START ===");
            for (String table : tables) {
                System.out.println();
                System.out.println("TABLE " + table);

                // Primary keys
                Set<String> pks = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                try (ResultSet rs = meta.getPrimaryKeys(null, null, table)) {
                    while (rs.next()) {
                        String col = rs.getString("COLUMN_NAME");
                        if (col != null) pks.add(col);
                    }
                }
                if (!pks.isEmpty()) {
                    System.out.println("  PK: " + String.join(", ", pks));
                }

                // Columns
                List<String> cols = new ArrayList<>();
                try (ResultSet rs = meta.getColumns(null, null, table, "%")) {
                    while (rs.next()) {
                        String col = rs.getString("COLUMN_NAME");
                        String type = rs.getString("TYPE_NAME");
                        int size = rs.getInt("COLUMN_SIZE");
                        String nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")) ? "NULL" : "NOT NULL";

                        String typeStr = type;
                        if (size > 0 && isSizedType(type)) {
                            typeStr = type + "(" + size + ")";
                        }
                        cols.add(col + " : " + typeStr + " " + nullable);
                    }
                }
                cols.sort(String.CASE_INSENSITIVE_ORDER);
                for (String cdef : cols) {
                    System.out.println("  COL: " + cdef);
                }

                // Foreign keys (imported keys)
                List<String> fks = new ArrayList<>();
                try (ResultSet rs = meta.getImportedKeys(null, null, table)) {
                    while (rs.next()) {
                        String fkCol = rs.getString("FKCOLUMN_NAME");
                        String pkTable = rs.getString("PKTABLE_NAME");
                        String pkCol = rs.getString("PKCOLUMN_NAME");
                        if (fkCol == null || pkTable == null || pkCol == null) continue;
                        fks.add(fkCol + " -> " + pkTable + "." + pkCol);
                    }
                }
                fks.sort(String.CASE_INSENSITIVE_ORDER);
                for (String fk : fks) {
                    System.out.println("  FK: " + fk);
                }
            }
            System.out.println();
            System.out.println("=== SCHEMA DUMP END ===");
        }
    }

    private static boolean isSizedType(String type) {
        if (type == null) return false;
        String t = type.toUpperCase(Locale.ROOT);
        return t.contains("CHAR") || t.contains("VARCHAR") || t.contains("VARBINARY");
    }
}

