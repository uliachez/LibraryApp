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

        if (isAdmin) {
            buttonPanel.add(addButton);
            buttonPanel.add(deleteButton);
            buttonPanel.add(deleteDbButton);
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

        showAllButton.addActionListener(e -> updateTable());

        frame.setVisible(true);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
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

        } catch (SQLException e) {
            System.err.println("Ошибка при инициализации таблицы: " + e.getMessage());
        }
    }

    public void insertBook(String title, String author) throws SQLException {
        if (!isAdmin) return;
        String sql = "INSERT INTO books (title, author) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, author);
            stmt.executeUpdate();
        }
    }

    public void searchBookByTitle(String title) throws SQLException {
        String sql = "SELECT * FROM books WHERE title = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        String sql = "DELETE FROM books WHERE title = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.executeUpdate();
        }
    }

    public void deleteDatabase() throws SQLException {
        if (!isAdmin) return;
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE IF EXISTS library_db");
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
