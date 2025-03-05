import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        LibraryApp.initializeDatabase();
        SwingUtilities.invokeLater(LibraryApp::new);
    }
}
