import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class LibraryApp {
    private static final String URL = "jdbc:postgresql://localhost:5432/library_db";
    private static final String USER = "postgres";
    private static final String PASSWORD = "your_password";

    private JFrame frame;
    private JTextField titleField;
    private JTextField authorField;
    private JTable bookTable;
    private DefaultTableModel tableModel;

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Ошибка: Драйвер PostgreSQL не найден!", e);
        }
    }

    public LibraryApp() {
        initialize();
    }

    private void initialize() {
        frame = new JFrame("Библиотека");
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

        JPanel buttonPanel = new JPanel(new GridLayout(1, 6));
        JButton addButton = new JButton("Добавить книгу");
        JButton searchButton = new JButton("Найти книгу");
        JButton deleteButton = new JButton("Удалить книгу");
        JButton deleteDbButton = new JButton("Удалить базу данных");
        JButton clearTableButton = new JButton("Очистить таблицу");
        JButton showAllButton = new JButton("Показать все книги");

        buttonPanel.add(addButton);
        buttonPanel.add(searchButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(deleteDbButton);
        buttonPanel.add(clearTableButton);
        buttonPanel.add(showAllButton);

        tableModel = new DefaultTableModel(new Object[]{"ID", "Название", "Автор"}, 0);
        bookTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(bookTable);

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(buttonPanel, BorderLayout.CENTER);
        frame.add(scrollPane, BorderLayout.SOUTH);

        addButton.addActionListener(e -> {
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
            try {
                deleteDatabase();
                updateTable();
            } catch (SQLException ex) {
                showError("Ошибка при удалении базы данных: " + ex.getMessage());
            }
        });

        clearTableButton.addActionListener(e -> {
            try {
                clearTable();
                updateTable();
            } catch (SQLException ex) {
                showError("Ошибка при очистке таблицы: " + ex.getMessage());
            }
        });

        showAllButton.addActionListener(e -> updateTable());

        frame.setVisible(true);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            String createTable = """
                CREATE TABLE IF NOT EXISTS books (
                    id SERIAL PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    author VARCHAR(255) NOT NULL
                );
            """;

            stmt.executeUpdate(createTable);
        } catch (SQLException e) {
            System.err.println("Ошибка при инициализации базы данных: " + e.getMessage());
        }
    }

    public static void deleteDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE IF EXISTS library_db");
            JOptionPane.showMessageDialog(null, "База данных удалена!", "Информация", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public static void insertBook(String title, String author) throws SQLException {
        String sql = "INSERT INTO books (title, author) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, author);
            stmt.executeUpdate();
        }
    }

    public void searchBookByTitle(String title) throws SQLException {
        String sql = "SELECT * FROM books WHERE title = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            ResultSet rs = stmt.executeQuery();
            tableModel.setRowCount(0);
            while (rs.next()) {
                tableModel.addRow(new Object[]{rs.getInt("id"), rs.getString("title"), rs.getString("author")});
            }
        }
    }

    public static void deleteBookByTitle(String title) throws SQLException {
        String sql = "DELETE FROM books WHERE title = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.executeUpdate();
        }
    }

    public static void clearTable() throws SQLException {
        String sql = "DELETE FROM books";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void updateTable() {
        String sql = "SELECT * FROM books";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
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
