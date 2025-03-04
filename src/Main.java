import javax.swing.*;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        try {
            LibraryApp.createDatabaseAndTable();
            LibraryApp.createProcedures();
        } catch (SQLException e) {
            System.err.println("Ошибка при настройке БД: " + e.getMessage());
        }
        SwingUtilities.invokeLater(LibraryApp::new);
    }
}
