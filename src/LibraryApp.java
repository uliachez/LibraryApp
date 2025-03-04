import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;

public class LibraryApp {
    private static final String URL = "jdbc:postgresql://localhost:5432/library_db";
    private static final String USER = "postgres";
    private static final String PASSWORD = "your_password";

    private JFrame frame;
    private JTextField titleField;
    private JTextField authorField;
    private JTextArea outputArea;

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
        frame.setSize(400, 300);
        frame.setLayout(new GridLayout(5, 1));

        titleField = new JTextField();
        authorField = new JTextField();
        JButton addButton = new JButton("Добавить книгу");
        JButton searchButton = new JButton("Найти книгу");
        JButton deleteButton = new JButton("Удалить книгу");
        outputArea = new JTextArea();
        outputArea.setEditable(false);

        frame.add(new JLabel("Название книги:"));
        frame.add(titleField);
        frame.add(new JLabel("Автор:"));
        frame.add(authorField);
        frame.add(addButton);
        frame.add(searchButton);
        frame.add(deleteButton);
        frame.add(new JScrollPane(outputArea));

        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String title = titleField.getText();
                String author = authorField.getText();
                if (!title.isEmpty() && !author.isEmpty()) {
                    try {
                        insertBook(title, author);
                        outputArea.setText("Книга добавлена: " + title);
                    } catch (SQLException ex) {
                        outputArea.setText("Ошибка при добавлении книги: " + ex.getMessage());
                    }
                } else {
                    outputArea.setText("Ошибка: заполните все поля!");
                }
            }
        });

        searchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String title = titleField.getText();
                if (!title.isEmpty()) {
                    try {
                        String bookInfo = searchBookByTitle(title);
                        outputArea.setText(bookInfo.isEmpty() ? "Книга не найдена." : bookInfo);
                    } catch (SQLException ex) {
                        outputArea.setText("Ошибка при поиске книги: " + ex.getMessage());
                    }
                } else {
                    outputArea.setText("Введите название книги для поиска.");
                }
            }
        });

        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String title = titleField.getText();
                if (!title.isEmpty()) {
                    try {
                        deleteBookByTitle(title);
                        outputArea.setText("Книга удалена: " + title);
                    } catch (SQLException ex) {
                        outputArea.setText("Ошибка при удалении книги: " + ex.getMessage());
                    }
                } else {
                    outputArea.setText("Введите название книги для удаления.");
                }
            }
        });

        frame.setVisible(true);
    }

    public static void createDatabaseAndTable() throws SQLException {
        String dbUrl = "jdbc:postgresql://localhost:5432/postgres";
        String dbName = "library_db";

        try (Connection conn = DriverManager.getConnection(dbUrl, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            String checkDbExists = "SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'";
            ResultSet rs = stmt.executeQuery(checkDbExists);
            if (!rs.next()) {
                stmt.executeUpdate("CREATE DATABASE " + dbName);
                System.out.println("База данных '" + dbName + "' создана.");
            } else {
                System.out.println("База данных уже существует.");
            }
        }

        String dbConnectionUrl = "jdbc:postgresql://localhost:5432/" + dbName;
        try (Connection conn = DriverManager.getConnection(dbConnectionUrl, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS books (
                    id SERIAL PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    author VARCHAR(255) NOT NULL
                );
            """;
            stmt.executeUpdate(createTableSQL);
            System.out.println("Таблица 'books' проверена или создана.");
        }
    }

    public static void createProcedures() throws SQLException {
        String createAddBookFunction = """
            CREATE OR REPLACE FUNCTION add_book(title_param VARCHAR, author_param VARCHAR) RETURNS VOID AS $$
            BEGIN
                INSERT INTO books (title, author) VALUES (title_param, author_param);
            END;
            $$ LANGUAGE plpgsql;
        """;

        String createFindBookFunction = """
            CREATE OR REPLACE FUNCTION find_book_by_title(title_param VARCHAR) RETURNS TABLE(title VARCHAR, author VARCHAR) AS $$
            BEGIN
                RETURN QUERY SELECT title, author FROM books WHERE title = title_param;
            END;
            $$ LANGUAGE plpgsql;
        """;

        String createRemoveBookFunction = """
            CREATE OR REPLACE FUNCTION remove_book_by_title(title_param VARCHAR) RETURNS VOID AS $$
            BEGIN
                DELETE FROM books WHERE title = title_param;
            END;
            $$ LANGUAGE plpgsql;
        """;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createAddBookFunction);
            stmt.executeUpdate(createFindBookFunction);
            stmt.executeUpdate(createRemoveBookFunction);
            System.out.println("Хранимые процедуры успешно созданы или обновлены.");
        }
    }

    public static void insertBook(String title, String author) throws SQLException {
        String sql = "{CALL add_book(?, ?)}";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, author);
            stmt.execute();
        }
    }

    public static String searchBookByTitle(String title) throws SQLException {
        String sql = "SELECT * FROM find_book_by_title(?)";
        StringBuilder result = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.append("Найдено: ").append(rs.getString("title")).append(" - ")
                        .append(rs.getString("author")).append("\n");
            }
        }
        return result.toString();
    }

    public static void deleteBookByTitle(String title) throws SQLException {
        String sql = "{CALL remove_book_by_title(?)}";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.setString(1, title);
            stmt.execute();
        }
    }
}
