import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        String[] options = {"Администратор", "Гость"};
        int choice = JOptionPane.showOptionDialog(
                null,
                "Выберите режим доступа:",
                "Авторизация",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        String user, password;
        if (choice == 0) {
            user = "admin_user";
            password = "admin_password";
        } else {
            user = "guest_user";
            password = "guest_password";
        }

        LibraryApp.initializeDatabase(user, password);
        SwingUtilities.invokeLater(() -> new LibraryApp(user, password));
    }
}
