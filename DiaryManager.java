import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiaryManager extends Application {

    private static final String DATA_DIR = "diary_entries";
    private static final DateTimeFormatter FILE_NAME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    // In a real app, derive this from a user password!
    private static final String SECRET_KEY = "MySuperSecretKey"; 

    private final ObservableList<DiaryEntry> entries = FXCollections.observableArrayList();
    private FilteredList<DiaryEntry> filteredEntries;
    private DiaryEntry currentEntry;

    private ListView<DiaryEntry> entryListView;
    private StackPane contentArea;
    private HTMLEditor editor;
    private WebView webView;
    private TextField searchField;
    private Label statusLabel;
    private Label wordCountLabel;
    private BorderPane rootLayout;
    private ToggleButton editModeToggle;
    private boolean isDarkMode = false;

    // Auto-save logic
    private PauseTransition autoSaveTimer;
    private boolean isDirty = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        ensureDataDirectoryExists();

        // --- Layout Setup ---
        rootLayout = new BorderPane();
        rootLayout.getStyleClass().add("main-container");

        // 1. Left Pane: Navigation & Search
        VBox sidebar = createSidebar();

        // 2. Center Pane: Editor
        contentArea = new StackPane();
        editor = new HTMLEditor();
        webView = new WebView();
        
        editor.setVisible(false);
        webView.setVisible(true);
        
        // Auto-save trigger
        editor.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            isDirty = true;
            updateStatus("Editing...");
            updateWordCount();
            autoSaveTimer.playFromStart();
        });
        
        contentArea.getChildren().addAll(webView, editor);

        // SplitPane for resizable sidebar
        SplitPane splitPane = new SplitPane(sidebar, contentArea);
        splitPane.setDividerPositions(0.3);
        rootLayout.setCenter(splitPane);

        // 3. Top Pane: Toolbar
        HBox topBar = createTopBar();
        rootLayout.setTop(topBar);

        // 4. Bottom Pane: Status
        rootLayout.setBottom(createStatusBar());

        // --- Scene & Styling ---
        Scene scene = new Scene(rootLayout, 1000, 700);
        String cssPath = Path.of("styles.css").toUri().toString();
        scene.getStylesheets().add(cssPath);

        primaryStage.setTitle("Personal Diary Manager");
        primaryStage.setScene(scene);
        primaryStage.show();

        // --- Initialization ---
        setupAutoSave();
        refreshEntryList();
    }

    private void ensureDataDirectoryExists() {
        try {
            Files.createDirectories(Path.of(DATA_DIR));
        } catch (IOException e) {
            showError("Initialization Error", "Could not create data directory.");
        }
    }

    private VBox createSidebar() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        box.setPrefWidth(250);
        box.getStyleClass().add("sidebar");

        Label listHeader = new Label("My Entries");
        listHeader.getStyleClass().add("header-label");

        searchField = new TextField();
        searchField.setPromptText("Search entries...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredEntries.setPredicate(entry -> {
                if (newVal == null || newVal.isEmpty()) return true;
                String lowerCaseFilter = newVal.toLowerCase();
                return entry.getTitle().toLowerCase().contains(lowerCaseFilter);
            });
            if (currentEntry != null) loadEntry(currentEntry); // Reload to apply highlight
        });

        entryListView = new ListView<>();
        filteredEntries = new FilteredList<>(entries, p -> true);
        entryListView.setItems(filteredEntries);
        entryListView.setCellFactory(param -> new ListCell<DiaryEntry>() {
            @Override
            protected void updateItem(DiaryEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox vBox = new VBox(2);
                    Label title = new Label(item.getTitle()); // Date
                    title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                    Label subtitle = new Label(item.getTime()); // Time
                    subtitle.setStyle("-fx-text-fill: gray; -fx-font-size: 12px;");
                    vBox.getChildren().addAll(title, subtitle);
                    setGraphic(vBox);
                }
            }
        });

        entryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (isDirty && oldVal != null) saveEntry(oldVal, editor.getHtmlText());
            loadEntry(newVal);
        });

        box.getChildren().addAll(listHeader, searchField, entryListView);
        return box;
    }

    private HBox createTopBar() {
        HBox hbox = new HBox(10);
        hbox.setPadding(new Insets(10));
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.getStyleClass().add("toolbar");

        editModeToggle = new ToggleButton("Edit Mode");
        editModeToggle.setSelected(false);
        editModeToggle.setDisable(true);
        editModeToggle.setOnAction(e -> toggleEditMode(editModeToggle.isSelected()));

        Button newBtn = new Button("New Entry");
        newBtn.setOnAction(e -> createNewEntry());

        Button deleteBtn = new Button("Delete");
        deleteBtn.setOnAction(e -> deleteCurrentEntry());

        Button printBtn = new Button("Print");
        printBtn.setOnAction(e -> printCurrentEntry());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToggleButton themeToggle = new ToggleButton("Dark Mode");
        themeToggle.setOnAction(e -> {
            isDarkMode = themeToggle.isSelected();
            if (isDarkMode) {
                rootLayout.getStyleClass().add("dark-mode");
            } else {
                rootLayout.getStyleClass().remove("dark-mode");
            }
        });

        hbox.getChildren().addAll(newBtn, deleteBtn, printBtn, new Separator(), editModeToggle, spacer, themeToggle);
        return hbox;
    }

    private HBox createStatusBar() {
        HBox box = new HBox(20);
        box.setPadding(new Insets(5, 10, 5, 10));
        box.setStyle("-fx-background-color: #e0e0e0;");
        
        statusLabel = new Label("Status: Ready");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        wordCountLabel = new Label("Words: 0");
        
        box.getChildren().addAll(statusLabel, spacer, wordCountLabel);
        return box;
    }

    private void updateStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText("Status: " + msg));
    }

    private void setupAutoSave() {
        autoSaveTimer = new PauseTransition(Duration.seconds(2));
        autoSaveTimer.setOnFinished(e -> {
            if (currentEntry != null && isDirty) {
                saveEntry(currentEntry, editor.getHtmlText());
            }
        });
    }

    private void toggleEditMode(boolean isEditing) {
        if (currentEntry == null) return;
        
        if (isEditing) {
            editor.setVisible(true);
            webView.setVisible(false);
            editor.requestFocus();
        } else {
            // Switching to Read Mode
            if (isDirty) saveEntry(currentEntry, editor.getHtmlText());
            webView.getEngine().loadContent(editor.getHtmlText());
            editor.setVisible(false);
            webView.setVisible(true);
        }
    }

    private void updateWordCount() {
        String text = editor.getHtmlText().replaceAll("<[^>]*>", "").trim();
        int count = text.isEmpty() ? 0 : text.split("\\s+").length;
        Platform.runLater(() -> wordCountLabel.setText("Words: " + count));
    }

    // --- Core Logic ---

    private void createNewEntry() {
        String fileName = LocalDateTime.now().format(FILE_NAME_FMT) + ".html";
        Path path = Path.of(DATA_DIR, fileName);
        DiaryEntry newEntry = new DiaryEntry(path);
        
        // Create empty file
        try {
            Files.writeString(path, "");
            entries.add(0, newEntry);
            entryListView.getSelectionModel().select(newEntry);
            editModeToggle.setSelected(true); // Auto enter edit mode
            toggleEditMode(true);
            updateStatus("Created new entry.");
        } catch (IOException e) {
            showError("Error", "Could not create new entry file.");
        }
    }

    private void loadEntry(DiaryEntry entry) {
        if (entry == null) {
            currentEntry = null;
            editor.setHtmlText("");
            webView.getEngine().loadContent("");
            editModeToggle.setDisable(true);
            return;
        }

        currentEntry = entry;
        editModeToggle.setDisable(false);
        
        Task<String> loadTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                String rawContent = Files.readString(entry.getPath());
                try {
                    return decrypt(rawContent);
                } catch (Exception e) {
                    // Fallback for old plain-text files
                    return rawContent;
                }
            }
        };

        loadTask.setOnSucceeded(e -> {
            String content = loadTask.getValue();
            editor.setHtmlText(content);
            
            // Apply search highlighting for Read Mode
            String viewContent = applyHighlighting(content, searchField.getText());
            webView.getEngine().loadContent(viewContent);
            
            isDirty = false;
            updateStatus("Loaded " + entry.getTitle());
            updateWordCount();
            toggleEditMode(editModeToggle.isSelected());
        });

        loadTask.setOnFailed(e -> {
            showError("Read Error", "Could not load entry.");
        });

        Thread.ofVirtual().start(loadTask);
    }

    private void saveEntry(DiaryEntry entry, String content) {
        Task<Void> saveTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                String encrypted = encrypt(content);
                Files.writeString(entry.getPath(), encrypted);
                return null;
            }
        };

        saveTask.setOnSucceeded(e -> {
            // Only mark as clean if the content hasn't changed since save started
            if (editor.getHtmlText().equals(content)) {
                isDirty = false;
                updateStatus("Saved " + entry.getTitle());
            }
        });

        saveTask.setOnFailed(e -> {
            updateStatus("Save Failed!");
            showError("Save Error", "Could not save entry.");
        });

        Thread.ofVirtual().start(saveTask);
    }

    private void deleteCurrentEntry() {
        DiaryEntry selected = entryListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Entry");
        alert.setHeaderText("Are you sure you want to delete this entry?");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Files.deleteIfExists(selected.getPath());
                entries.remove(selected);
                updateStatus("Deleted entry.");
            } catch (IOException e) {
                showError("Delete Error", "Could not delete file.");
            }
        }
    }

    private void printCurrentEntry() {
        if (currentEntry == null) return;
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(rootLayout.getScene().getWindow())) {
            webView.getEngine().print(job);
            job.endJob();
            updateStatus("Printed entry.");
        }
    }

    private void refreshEntryList() {
        Task<List<DiaryEntry>> loadListTask = new Task<List<DiaryEntry>>() {
            @Override
            protected List<DiaryEntry> call() throws Exception {
                try (Stream<Path> paths = Files.list(Path.of(DATA_DIR))) {
                    return paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".html"))
                        .map(DiaryEntry::new)
                        .sorted(Comparator.comparing(DiaryEntry::getTimestamp).reversed())
                        .collect(Collectors.toList());
                }
            }
        };

        loadListTask.setOnSucceeded(e -> {
            entries.setAll(loadListTask.getValue());
            updateStatus("Entry list refreshed.");
        });

        Thread.ofVirtual().start(loadListTask);
    }

    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    // --- Helper Methods ---

    private String applyHighlighting(String html, String term) {
        if (term == null || term.isEmpty()) return html;
        // Simple highlighting: wraps text in a yellow span. 
        // Note: This is a basic implementation and might affect HTML tags if they match the term.
        return html.replaceAll("(?i)(" + Pattern.quote(term) + ")", 
            "<span style='background-color: #ffeb3b; color: black;'>$1</span>");
    }

    // --- Encryption Helpers ---
    
    private static Key getKey() {
        // Pad key to 16 bytes (128 bit)
        String keyStr = String.format("%-16s", SECRET_KEY).substring(0, 16);
        return new SecretKeySpec(keyStr.getBytes(StandardCharsets.UTF_8), "AES");
    }

    private String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String decrypt(String encryptedData) throws Exception {
        // Check if data looks like Base64 (basic validation)
        if (!encryptedData.matches("^[A-Za-z0-9+/=]+$")) {
            throw new IllegalArgumentException("Not encrypted");
        }
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, getKey());
        byte[] original = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(original, StandardCharsets.UTF_8);
    }

    // --- Model Class ---
    private static class DiaryEntry {
        private final Path path;
        private final LocalDateTime timestamp;

        public DiaryEntry(Path path) {
            this.path = path;
            String filename = path.getFileName().toString().replace(".html", "");
            LocalDateTime ts;
            try {
                ts = LocalDateTime.parse(filename, FILE_NAME_FMT);
            } catch (Exception e) {
                ts = LocalDateTime.now(); // Fallback
            }
            this.timestamp = ts;
        }

        public Path getPath() { return path; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getTitle() { return timestamp.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")); }
        public String getTime() { return timestamp.format(DateTimeFormatter.ofPattern("HH:mm")); }
    }
}
