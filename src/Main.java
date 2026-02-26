import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DataStore dataStore = new DataStore();
            dataStore.seed();
            new LoginFrame(dataStore).setVisible(true);
        });
    }
}

enum Role {
    ADMIN, USER
}

class User {
    private static int seq = 1;
    final int id;
    final String login;
    final String password;
    final Role role;
    boolean voted;

    User(String login, String password, Role role) {
        this.id = seq++;
        this.login = login;
        this.password = password;
        this.role = role;
        this.voted = false;
    }
}

class Song {
    private static int seq = 1;
    final int id;
    String title;
    String artist;
    int durationMinutes;
    int votes;

    Song(String title, String artist, int durationMinutes) {
        this.id = seq++;
        this.title = title;
        this.artist = artist;
        this.durationMinutes = durationMinutes;
        this.votes = 0;
    }
}

class Vote {
    private static int seq = 1;
    final int id;
    final int userId;
    final int songId;

    Vote(int userId, int songId) {
        this.id = seq++;
        this.userId = userId;
        this.songId = songId;
    }
}

class DataStore {
    final List<User> users = new ArrayList<>();
    final List<Song> songs = new ArrayList<>();
    final List<Vote> votes = new ArrayList<>();

    int maxVoteLimit = 3;
    boolean votingClosed = false;

    void seed() {
        users.add(new User("admin", "admin", Role.ADMIN));
        users.add(new User("listener", "1234", Role.USER));

        songs.add(new Song("Танець пінгвіна", "Океан Ельзи", 4));
        songs.add(new Song("Ніч яка місячна", "Скрябін", 5));
        songs.add(new Song("Шлях до мрії", "Антитіла", 4));
        songs.add(new Song("Зоряний дощ", "Pianoбой", 6));
    }

    User findUser(String login, String password) {
        return users.stream()
                .filter(u -> u.login.equals(login) && u.password.equals(password))
                .findFirst()
                .orElse(null);
    }

    boolean loginExists(String login) {
        return users.stream().anyMatch(u -> u.login.equalsIgnoreCase(login));
    }

    void registerUser(String login, String password) {
        users.add(new User(login, password, Role.USER));
    }

    Song findSongById(int id) {
        return songs.stream().filter(s -> s.id == id).findFirst().orElse(null);
    }

    void resetVotesForSong(int songId) {
        votes.removeIf(v -> v.songId == songId);
        Song song = findSongById(songId);
        if (song != null) {
            song.votes = (int) votes.stream().filter(v -> v.songId == songId).count();
        }
    }

    void recalculateAllVotes() {
        for (Song song : songs) {
            song.votes = (int) votes.stream().filter(v -> v.songId == song.id).count();
        }
    }
}

class LoginFrame extends JFrame {
    private final DataStore dataStore;
    private final JTextField loginField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();

    LoginFrame(DataStore dataStore) {
        this.dataStore = dataStore;
        setTitle("Концерт на замовлення — Авторизація");
        setSize(400, 220);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        form.add(new JLabel("Логін:"));
        form.add(loginField);
        form.add(new JLabel("Пароль:"));
        form.add(passwordField);

        JButton loginButton = uiButton("Увійти");
        JButton registerButton = uiButton("Реєстрація");

        loginButton.addActionListener(e -> login());
        registerButton.addActionListener(e -> register());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttons.add(loginButton);
        buttons.add(registerButton);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private JButton uiButton(String text) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(130, 30));
        button.setFont(new Font("SansSerif", Font.PLAIN, 14));
        return button;
    }

    private void login() {
        String login = loginField.getText().trim();
        String password = new String(passwordField.getPassword());
        User user = dataStore.findUser(login, password);
        if (user == null) {
            JOptionPane.showMessageDialog(this, "Невірний логін або пароль.", "Помилка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        new MainFrame(dataStore, user).setVisible(true);
        dispose();
    }

    private void register() {
        String login = loginField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (login.isBlank() || password.isBlank()) {
            JOptionPane.showMessageDialog(this, "Заповніть логін і пароль.", "Помилка", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (dataStore.loginExists(login)) {
            JOptionPane.showMessageDialog(this, "Користувач із таким логіном вже існує.", "Помилка", JOptionPane.WARNING_MESSAGE);
            return;
        }

        dataStore.registerUser(login, password);
        JOptionPane.showMessageDialog(this, "Реєстрація успішна. Тепер увійдіть у систему.");
    }
}

class MainFrame extends JFrame {
    private final DataStore dataStore;
    private final User user;
    private final DefaultTableModel songsModel;
    private final JTable songsTable;
    private final JTextArea outputArea = new JTextArea();
    private final JLabel statusLabel = new JLabel();

    private final JButton addButton = uiButton("Додати пісню");
    private final JButton deleteButton = uiButton("Видалити");
    private final JButton voteButton = uiButton("Проголосувати");
    private final JButton saveLimitButton = uiButton("Зберегти ліміт");
    private final JButton closeVotingButton = uiButton("Сформувати концерт");

    private final JTextField titleField = new JTextField();
    private final JTextField artistField = new JTextField();
    private final JTextField durationField = new JTextField();

    private final JTextField limitField = new JTextField();
    private final JTextField concertDurationField = new JTextField("90");

    MainFrame(DataStore dataStore, User user) {
        this.dataStore = dataStore;
        this.user = user;

        setTitle("Концерт на замовлення — " + (user.role == Role.ADMIN ? "Адміністратор" : "Слухач") + " (" + user.login + ")");
        setSize(960, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        songsModel = new DefaultTableModel(new Object[]{"ID", "Назва", "Виконавець", "Тривалість (хв)", "Голоси"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        songsTable = new JTable(songsModel);
        songsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        songsTable.getSelectionModel().addListSelectionListener(e -> updateButtonState());

        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 13));

        setLayout(new BorderLayout(8, 8));
        add(buildTabs(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        refreshSongs();
        updateButtonState();
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Список пісень", buildSongsPanel());
        if (user.role == Role.ADMIN) {
            tabs.addTab("Статистика та Звіт", buildAdminPanel());
        } else {
            tabs.addTab("Мій вибір", buildUserPanel());
        }
        return tabs;
    }

    private JPanel buildSongsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        panel.add(new JScrollPane(songsTable), BorderLayout.CENTER);

        JPanel control = new JPanel(new GridLayout(3, 4, 8, 8));
        control.add(new JLabel("Назва"));
        control.add(titleField);
        control.add(new JLabel("Виконавець"));
        control.add(artistField);
        control.add(new JLabel("Тривалість (хв)"));
        control.add(durationField);
        control.add(addButton);
        control.add(deleteButton);
        control.add(voteButton);

        addButton.addActionListener(e -> addSong());
        deleteButton.addActionListener(e -> deleteSong());
        voteButton.addActionListener(e -> voteForSongs());

        panel.add(control, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildUserPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Пояснення"));
        JTextArea info = new JTextArea();
        info.setEditable(false);
        info.setText("Оберіть від 1 до " + dataStore.maxVoteLimit + " пісень у вкладці 'Список пісень'\n"
                + "та натисніть 'Проголосувати'. Повторне голосування заборонено.");
        panel.add(info, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new GridLayout(2, 4, 8, 8));
        limitField.setText(String.valueOf(dataStore.maxVoteLimit));
        top.add(new JLabel("Ліміт голосів N"));
        top.add(limitField);
        top.add(saveLimitButton);
        top.add(new JLabel());

        top.add(new JLabel("Тривалість концерту (хв)"));
        top.add(concertDurationField);
        top.add(closeVotingButton);
        top.add(new JLabel());

        saveLimitButton.addActionListener(e -> saveLimit());
        closeVotingButton.addActionListener(e -> finalizeConcert());

        outputArea.setBorder(BorderFactory.createTitledBorder("Програма концерту"));

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBottom() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        panel.add(statusLabel, BorderLayout.WEST);
        updateStatus();
        return panel;
    }

    private JButton uiButton(String text) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(170, 30));
        button.setFont(new Font("SansSerif", Font.PLAIN, 14));
        return button;
    }

    private void refreshSongs() {
        dataStore.recalculateAllVotes();
        songsModel.setRowCount(0);
        for (Song song : dataStore.songs) {
            songsModel.addRow(new Object[]{song.id, song.title, song.artist, song.durationMinutes, song.votes});
        }
        updateStatus();
    }

    private void updateButtonState() {
        boolean songSelected = songsTable.getSelectedRowCount() > 0;
        deleteButton.setEnabled(songSelected && user.role == Role.ADMIN);
        voteButton.setEnabled(songSelected && user.role == Role.USER && !user.voted && !dataStore.votingClosed);
        addButton.setEnabled(user.role == Role.ADMIN);
        titleField.setEnabled(user.role == Role.ADMIN);
        artistField.setEnabled(user.role == Role.ADMIN);
        durationField.setEnabled(user.role == Role.ADMIN);
    }

    private void addSong() {
        String title = titleField.getText().trim();
        String artist = artistField.getText().trim();
        String durationText = durationField.getText().trim();

        if (title.isBlank() || artist.isBlank() || durationText.isBlank()) {
            JOptionPane.showMessageDialog(this, "Заповніть назву, виконавця та тривалість.", "Помилка", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int duration;
        try {
            duration = Integer.parseInt(durationText);
            if (duration <= 0) {
                throw new NumberFormatException("<=0");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Тривалість має бути додатним числом.", "Помилка", JOptionPane.WARNING_MESSAGE);
            return;
        }

        dataStore.songs.add(new Song(title, artist, duration));
        titleField.setText("");
        artistField.setText("");
        durationField.setText("");
        refreshSongs();
    }

    private void deleteSong() {
        int selected = songsTable.getSelectedRow();
        if (selected < 0) {
            return;
        }
        int songId = (int) songsModel.getValueAt(selected, 0);
        dataStore.songs.removeIf(song -> song.id == songId);
        dataStore.votes.removeIf(v -> v.songId == songId);
        refreshSongs();
    }

    private void voteForSongs() {
        if (user.voted) {
            JOptionPane.showMessageDialog(this, "Ви вже голосували.", "Попередження", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (dataStore.votingClosed) {
            JOptionPane.showMessageDialog(this, "Голосування вже закрито.", "Попередження", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int[] selectedRows = songsTable.getSelectedRows();
        int countSelected = selectedRows.length;
        if (countSelected < 1 || countSelected > dataStore.maxVoteLimit) {
            JOptionPane.showMessageDialog(
                    this,
                    "Потрібно обрати від 1 до " + dataStore.maxVoteLimit + " пісень.\nОбрано: " + countSelected,
                    "Перевищення ліміту",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        for (int row : selectedRows) {
            int songId = (int) songsModel.getValueAt(row, 0);
            dataStore.votes.add(new Vote(user.id, songId));
        }
        user.voted = true;
        refreshSongs();
        updateButtonState();
        JOptionPane.showMessageDialog(this, "Ваш голос збережено.");
    }

    private void saveLimit() {
        String txt = limitField.getText().trim();
        try {
            int newLimit = Integer.parseInt(txt);
            if (newLimit <= 0) {
                throw new NumberFormatException("<=0");
            }
            dataStore.maxVoteLimit = newLimit;
            JOptionPane.showMessageDialog(this, "Ліміт голосів оновлено: " + newLimit);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Введіть коректний додатний ліміт.", "Помилка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void finalizeConcert() {
        int maxMinutes;
        try {
            maxMinutes = Integer.parseInt(concertDurationField.getText().trim());
            if (maxMinutes <= 0) {
                throw new NumberFormatException("<=0");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Некоректна тривалість концерту.", "Помилка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        dataStore.votingClosed = true;
        dataStore.recalculateAllVotes();

        List<Song> sorted = dataStore.songs.stream()
                .sorted(Comparator.comparingInt((Song s) -> s.votes).reversed()
                        .thenComparing(s -> s.title.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        List<Song> concertSongs = new ArrayList<>();
        int total = 0;
        for (Song song : sorted) {
            if (total + song.durationMinutes <= maxMinutes) {
                concertSongs.add(song);
                total += song.durationMinutes;
            }
        }

        String report = buildConcertReport(concertSongs, maxMinutes, total);
        outputArea.setText(report);

        try {
            String name = "concert_program_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
            Path pdfPath = Path.of(name);
            SimplePdfWriter.writePdf(pdfPath, report);
            JOptionPane.showMessageDialog(this, "Голосування закрито. Звіт збережено: " + pdfPath.toAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Не вдалося зберегти PDF: " + ex.getMessage(), "Помилка", JOptionPane.ERROR_MESSAGE);
        }

        updateButtonState();
    }

    private String buildConcertReport(List<Song> songs, int limit, int total) {
        StringBuilder sb = new StringBuilder();
        sb.append("ПРОГРАМА КОНЦЕРТУ\n");
        sb.append("==============================\n");
        sb.append("Ліміт тривалості: ").append(limit).append(" хв\n");
        sb.append("Фактична тривалість: ").append(total).append(" хв\n\n");

        if (songs.isEmpty()) {
            sb.append("Немає пісень для програми.\n");
            return sb.toString();
        }

        int i = 1;
        for (Song s : songs) {
            sb.append(String.format(Locale.ROOT, "%d) %s — %s (%d хв, голосів: %d)%n",
                    i++, s.title, s.artist, s.durationMinutes, s.votes));
        }
        return sb.toString();
    }

    private void updateStatus() {
        String status = "Роль: " + (user.role == Role.ADMIN ? "Адміністратор" : "Слухач")
                + " | Ліміт N=" + dataStore.maxVoteLimit
                + " | К-сть пісень=" + dataStore.songs.size()
                + " | Голосування: " + (dataStore.votingClosed ? "закрито" : "відкрите");
        statusLabel.setText(status);
    }
}

class SimplePdfWriter {
    private SimplePdfWriter() {}

    public static void writePdf(Path path, String content) throws IOException {
        List<String> lines = content.lines().collect(Collectors.toList());
        String stream = buildTextStream(lines);

        String obj1 = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n";
        String obj2 = "2 0 obj\n<< /Type /Pages /Count 1 /Kids [3 0 R] >>\nendobj\n";
        String obj3 = "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n";
        String obj4 = "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n";

        byte[] streamBytes = stream.getBytes(StandardCharsets.ISO_8859_1);
        String obj5Header = "5 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n";
        String obj5Footer = "\nendstream\nendobj\n";

        String header = "%PDF-1.4\n";

        List<byte[]> chunks = new ArrayList<>();
        chunks.add(header.getBytes(StandardCharsets.ISO_8859_1));

        int offset1 = size(chunks);
        chunks.add(obj1.getBytes(StandardCharsets.ISO_8859_1));
        int offset2 = size(chunks);
        chunks.add(obj2.getBytes(StandardCharsets.ISO_8859_1));
        int offset3 = size(chunks);
        chunks.add(obj3.getBytes(StandardCharsets.ISO_8859_1));
        int offset4 = size(chunks);
        chunks.add(obj4.getBytes(StandardCharsets.ISO_8859_1));
        int offset5 = size(chunks);
        chunks.add(obj5Header.getBytes(StandardCharsets.ISO_8859_1));
        chunks.add(streamBytes);
        chunks.add(obj5Footer.getBytes(StandardCharsets.ISO_8859_1));

        int xrefOffset = size(chunks);
        String xref = "xref\n0 6\n"
                + "0000000000 65535 f \n"
                + formatOffset(offset1)
                + formatOffset(offset2)
                + formatOffset(offset3)
                + formatOffset(offset4)
                + formatOffset(offset5)
                + "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"
                + xrefOffset + "\n%%EOF\n";
        chunks.add(xref.getBytes(StandardCharsets.ISO_8859_1));

        int totalSize = size(chunks);
        byte[] pdfBytes = new byte[totalSize];
        int pos = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, pdfBytes, pos, chunk.length);
            pos += chunk.length;
        }

        Files.write(path, pdfBytes);
    }

    private static String buildTextStream(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("BT\n/F1 12 Tf\n50 790 Td\n14 TL\n");
        for (String line : lines) {
            String safe = escapePdfText(transliterate(line));
            sb.append("(").append(safe).append(") Tj\nT*\n");
        }
        sb.append("ET");
        return sb.toString();
    }

    private static String transliterate(String input) {
        String out = input;
        out = out.replace("А", "A").replace("а", "a");
        out = out.replace("Б", "B").replace("б", "b");
        out = out.replace("В", "V").replace("в", "v");
        out = out.replace("Г", "H").replace("г", "h");
        out = out.replace("Ґ", "G").replace("ґ", "g");
        out = out.replace("Д", "D").replace("д", "d");
        out = out.replace("Е", "E").replace("е", "e");
        out = out.replace("Є", "Ye").replace("є", "ie");
        out = out.replace("Ж", "Zh").replace("ж", "zh");
        out = out.replace("З", "Z").replace("з", "z");
        out = out.replace("И", "Y").replace("и", "y");
        out = out.replace("І", "I").replace("і", "i");
        out = out.replace("Ї", "Yi").replace("ї", "i");
        out = out.replace("Й", "Y").replace("й", "i");
        out = out.replace("К", "K").replace("к", "k");
        out = out.replace("Л", "L").replace("л", "l");
        out = out.replace("М", "M").replace("м", "m");
        out = out.replace("Н", "N").replace("н", "n");
        out = out.replace("О", "O").replace("о", "o");
        out = out.replace("П", "P").replace("п", "p");
        out = out.replace("Р", "R").replace("р", "r");
        out = out.replace("С", "S").replace("с", "s");
        out = out.replace("Т", "T").replace("т", "t");
        out = out.replace("У", "U").replace("у", "u");
        out = out.replace("Ф", "F").replace("ф", "f");
        out = out.replace("Х", "Kh").replace("х", "kh");
        out = out.replace("Ц", "Ts").replace("ц", "ts");
        out = out.replace("Ч", "Ch").replace("ч", "ch");
        out = out.replace("Ш", "Sh").replace("ш", "sh");
        out = out.replace("Щ", "Shch").replace("щ", "shch");
        out = out.replace("Ю", "Yu").replace("ю", "iu");
        out = out.replace("Я", "Ya").replace("я", "ia");
        out = out.replace("Ь", "").replace("ь", "");
        out = out.replace("'", "");
        return out;
    }

    private static String escapePdfText(String txt) {
        return txt.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private static String formatOffset(int offset) {
        return String.format(Locale.ROOT, "%010d 00000 n \n", offset);
    }

    private static int size(List<byte[]> chunks) {
        return chunks.stream().filter(Objects::nonNull).mapToInt(a -> a.length).sum();
    }
}
