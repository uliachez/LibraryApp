import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class LibraryApp {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/library_db";
    private final String USER;
    private final String PASSWORD;

    private JFrame frame;
    private JTextField titleField;
    private JTextField authorField;
    private JTable bookTable;
    private DefaultTableModel tableModel;
    private boolean isAdmin;

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Ошибка: Драйвер PostgreSQL не найден!", e);
        }
    }

    public LibraryApp(String user, String password) {
        this.USER = user;
        this.PASSWORD = password;
        this.isAdmin = user.equals("admin_user");
        initialize();
    }

    private void initialize() {
        frame = new JFrame("Библиотека - " + (isAdmin ? "Администратор" : "Гость"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new GridLayout(2, 2));
        titleField = new JTextField();
        authorField = new JTextField();
        inputPanel.add(new JLabel("Название книги:"));
        inputPanel.add(titleField);
        inputPanel.add(new JLabel("Автор:"));
        inputPanel.add(authorField);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 5));
        JButton addButton = new JButton("Добавить книгу");
        JButton searchButton = new JButton("Найти книгу");
        JButton deleteButton = new JButton("Удалить книгу");
        JButton deleteDbButton = new JButton("Удалить базу данных");
        JButton showAllButton = new JButton("Показать все книги");
        JButton clearTableButton = new JButton("Очистить таблицу");

        if (isAdmin) {
            buttonPanel.add(addButton);
            buttonPanel.add(deleteButton);
            buttonPanel.add(deleteDbButton);
            buttonPanel.add(clearTableButton);
        }
        buttonPanel.add(searchButton);
        buttonPanel.add(showAllButton);

        tableModel = new DefaultTableModel(new Object[]{"ID", "Название", "Автор"}, 0);
        bookTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(bookTable);

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(buttonPanel, BorderLayout.CENTER);
        frame.add(scrollPane, BorderLayout.SOUTH);

        addButton.addActionListener(e -> {
            if (!isAdmin) return;
            String title = titleField.getText();
            String author = authorField.getText();
            if (!title.isEmpty() && !author.isEmpty()) {
                try {
                    insertBook(title, author);
                    updateTable();
                } catch (SQLException ex) {
                    showError("Ошибка при добавлении книги: " + ex.getMessage());
                }
            } else {
                showError("Ошибка: заполните все поля!");
            }
        });

        searchButton.addActionListener(e -> {
            String title = titleField.getText();
            if (!title.isEmpty()) {
                try {
                    searchBookByTitle(title);
                } catch (SQLException ex) {
                    showError("Ошибка при поиске книги: " + ex.getMessage());
                }
            } else {
                showError("Введите название книги для поиска.");
            }
        });

        deleteButton.addActionListener(e -> {
            if (!isAdmin) return;
            int selectedRow = bookTable.getSelectedRow();
            if (selectedRow != -1) {
                String title = tableModel.getValueAt(selectedRow, 1).toString();
                try {
                    deleteBookByTitle(title);
                    updateTable();
                } catch (SQLException ex) {
                    showError("Ошибка при удалении книги: " + ex.getMessage());
                }
            } else {
                showError("Выберите книгу для удаления!");
            }
        });

        deleteDbButton.addActionListener(e -> {
            if (!isAdmin) return;
            try {
                deleteDatabase();
                updateTable();
            } catch (SQLException ex) {
                showError("Ошибка при удалении базы данных: " + ex.getMessage());
            }
        });

        clearTableButton.addActionListener(e -> {
            if (!isAdmin) return;
            try {
                clearTable();
                updateTable();
            } catch (SQLException ex) {
                showError("Ошибка при удалении таблицы: " + ex.getMessage());
            }
        });

        showAllButton.addActionListener(e -> updateTable());

        frame.setVisible(true);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    public void createBookTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS books (
                id SERIAL PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                author VARCHAR(255) NOT NULL
            );
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("Таблица books успешно создана!");
        }
    }


    public static void initializeDatabase(String user, String password) {
        String postgresUrl = "jdbc:postgresql://localhost:5432/postgres";

        try (Connection conn = DriverManager.getConnection(postgresUrl, user, password);
             Statement stmt = conn.createStatement()) {

            String checkDbQuery = "SELECT 1 FROM pg_database WHERE datname = 'library_db'";
            ResultSet rs = stmt.executeQuery(checkDbQuery);

            if (!rs.next()) {
                stmt.executeUpdate("CREATE DATABASE library_db");
                System.out.println("База данных library_db создана.");
            }

        } catch (SQLException e) {
            System.err.println("Ошибка при проверке/создании базы данных: " + e.getMessage());
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, user, password);
             Statement stmt = conn.createStatement()) {

            String createTable = """
            CREATE TABLE IF NOT EXISTS books (
                id SERIAL PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                author VARCHAR(255) NOT NULL
            );
        """;
            stmt.executeUpdate(createTable);
            System.out.println("Таблица books проверена/создана.");

            stmt.executeUpdate("""
            CREATE OR REPLACE FUNCTION insert_book(p_title VARCHAR, p_author VARCHAR) RETURNS VOID AS $$
            BEGIN
                INSERT INTO books (title, author) VALUES (p_title, p_author);
            END;
            $$ LANGUAGE plpgsql;
        """);

            stmt.executeUpdate("""
            CREATE OR REPLACE FUNCTION delete_book(p_title VARCHAR) RETURNS VOID AS $$
            BEGIN
                DELETE FROM books WHERE title = p_title;
            END;
            $$ LANGUAGE plpgsql;
        """);

            stmt.executeUpdate("""
            CREATE OR REPLACE FUNCTION search_book(p_title VARCHAR) RETURNS TABLE(id INT, title VARCHAR, author VARCHAR) AS $$
            BEGIN
                RETURN QUERY SELECT books.id, books.title, books.author FROM books WHERE books.title = p_title;
            END;
            $$ LANGUAGE plpgsql;
        """);

            stmt.executeUpdate("""
            CREATE OR REPLACE FUNCTION clear_books() RETURNS VOID AS $$
            BEGIN
                DELETE FROM books;
            END;
            $$ LANGUAGE plpgsql;
        """);

            System.out.println("Хранимые процедуры созданы.");
            stmt.executeUpdate("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'guest_user') THEN CREATE ROLE guest_user; END IF; END $$;");
            stmt.executeUpdate("GRANT CONNECT ON DATABASE library_db TO guest_user;");
            stmt.executeUpdate("GRANT USAGE ON SCHEMA public TO guest_user;");
            stmt.executeUpdate("GRANT SELECT ON ALL TABLES IN SCHEMA public TO guest_user;");
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO guest_user;");

            System.out.println("Права доступа для guest_user назначены.");

        } catch (SQLException e) {
            System.err.println("Ошибка при инициализации базы данных и хранимых процедур: " + e.getMessage());
        }
    }




    public void insertBook(String title, String author) throws SQLException {
        if (!isAdmin) return;
        String sql = "{CALL insert_book(?, ?)}";  // Call the stored procedure
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, author);
            stmt.execute();
        }
    }


    public void searchBookByTitle(String title) throws SQLException {
        String sql = "{CALL search_book(?)}";  // Call the stored procedure
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.setString(1, title);
            ResultSet rs = stmt.executeQuery();
            tableModel.setRowCount(0);
            while (rs.next()) {
                tableModel.addRow(new Object[]{rs.getInt("id"), rs.getString("title"), rs.getString("author")});
            }
        }
    }



    public void deleteBookByTitle(String title) throws SQLException {
        if (!isAdmin) return;
        String sql = "{CALL delete_book(?)}";  // Call the stored procedure
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.setString(1, title);
            stmt.execute();
        }
    }


    public void clearTable() throws SQLException {
        if (!isAdmin) return;
        String sql = "{CALL clear_books()}";  // Call the stored procedure
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.execute();
            JOptionPane.showMessageDialog(frame, "Все книги удалены!", "Информация", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void deleteDatabase() throws SQLException {
        if (!isAdmin) return;
        String sql = "DROP DATABASE IF EXISTS library_db";
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            JOptionPane.showMessageDialog(frame, "База данных удалена!", "Информация", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void updateTable() {
        String sql = "SELECT * FROM books";
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            tableModel.setRowCount(0);
            while (rs.next()) {
                tableModel.addRow(new Object[]{rs.getInt("id"), rs.getString("title"), rs.getString("author")});
            }
        } catch (SQLException e) {
            showError("Ошибка при загрузке всех книг: " + e.getMessage());
        }
    }
}
