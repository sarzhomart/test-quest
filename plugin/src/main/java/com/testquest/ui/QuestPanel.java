package com.testquest.ui;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.testquest.core.TaskGenerationService;
import com.testquest.model.QuestTask;
import com.testquest.model.VerificationResult;
import com.testquest.state.ApiKeyStore;
import com.testquest.state.QuestState;
import com.testquest.state.QuestStateService;
import com.testquest.verification.TaskVerificationService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JProgressBar;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

public final class QuestPanel {
    private static final String[] MODELS = {
            "gemini-3.6-flash",
            "gemini-3.1-pro-preview",
            "gemini-2.5-flash"
    };

    private final Project project;
    private final QuestStateService stateService;
    private final JPanel root = new JPanel(new BorderLayout(0, 8));
    private final JLabel levelLabel = new JBLabel();
    private final JLabel pointsLabel = new JBLabel();
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel progressText = new JBLabel();
    private final DefaultListModel<QuestTask> taskModel = new DefaultListModel<>();
    private final JList<QuestTask> taskList = new JBList<>(taskModel);
    private final JBTextArea details = new JBTextArea();
    private final JBTextArea activity = new JBTextArea();
    private final JBPasswordField apiKey = new JBPasswordField();
    private final JComboBox<String> model = new JComboBox<>(MODELS);
    private final JBCheckBox capture = new JBCheckBox("Capture Selenium snapshots automatically");
    private final JButton generate = new JButton("Get 3 quests");
    private final JButton verify = new JButton("Verify selected");

    public QuestPanel(Project project) {
        this.project = project;
        this.stateService = QuestStateService.getInstance(project);
        buildUi();
        loadSettings();
        refresh();
    }

    public JComponent component() {
        return root;
    }

    public JComponent preferredFocus() {
        return taskList;
    }

    private void buildUi() {
        root.setBorder(JBUI.Borders.empty(10));
        root.add(header(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Quests", questsTab());
        tabs.addTab("Settings", settingsTab());
        root.add(tabs, BorderLayout.CENTER);
    }

    private JComponent header() {
        JPanel panel = new JPanel(new BorderLayout(8, 4));
        JPanel labels = new JPanel(new BorderLayout());
        levelLabel.setFont(levelLabel.getFont().deriveFont(Font.BOLD, 16f));
        pointsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        labels.add(levelLabel, BorderLayout.WEST);
        labels.add(pointsLabel, BorderLayout.EAST);
        progress.setStringPainted(false);
        progress.setPreferredSize(new Dimension(100, 20));
        progressText.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel progressPanel = new JPanel(new BorderLayout(0, 4));
        progressPanel.add(progress, BorderLayout.NORTH);
        progressPanel.add(progressText, BorderLayout.SOUTH);
        panel.add(labels, BorderLayout.NORTH);
        panel.add(progressPanel, BorderLayout.SOUTH);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                JBUI.Borders.empty(10)
        ));
        return panel;
    }

    private JComponent questsTab() {
        taskList.setCellRenderer(new QuestTaskRenderer());
        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showTask(taskList.getSelectedValue());
            }
        });

        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setBorder(JBUI.Borders.empty(8));
        activity.setEditable(false);
        activity.setLineWrap(true);
        activity.setWrapStyleWord(true);
        activity.setRows(6);
        activity.setText("Run a Selenium test, then click “Get 3 quests”.");

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JBScrollPane(taskList),
                new JBScrollPane(details)
        );
        split.setResizeWeight(0.48);
        split.setBorder(null);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.add(generate);
        actions.add(verify);
        generate.addActionListener(event -> generateTasks());
        verify.addActionListener(event -> verifyTask());

        JPanel bottom = new JPanel(new BorderLayout(0, 5));
        bottom.add(actions, BorderLayout.NORTH);
        bottom.add(new JBScrollPane(activity), BorderLayout.CENTER);

        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBorder(JBUI.Borders.emptyTop(8));
        tab.add(split, BorderLayout.CENTER);
        tab.add(bottom, BorderLayout.SOUTH);
        return tab;
    }

    private JComponent settingsTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(14, 4));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, 6, 0);

        panel.add(bold("Gemini API key"), constraints);
        constraints.gridy++;
        apiKey.setToolTipText("Stored in the IntelliJ Password Safe, not in project files");
        panel.add(apiKey, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(12, 0, 6, 0);
        panel.add(bold("Model"), constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 6, 0);
        model.setEditable(true);
        panel.add(model, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(12, 0, 6, 0);
        panel.add(capture, constraints);

        constraints.gridy++;
        JButton save = new JButton("Save settings");
        save.addActionListener(event -> {
            saveSettings();
            notifyUser("Settings saved", NotificationType.INFORMATION);
        });
        panel.add(save, constraints);

        constraints.gridy++;
        constraints.weighty = 1;
        panel.add(new JPanel(), constraints);
        return panel;
    }

    private void loadSettings() {
        QuestState state = stateService.snapshot();
        apiKey.setText(ApiKeyStore.get(project));
        model.setSelectedItem(state.model);
        capture.setSelected(state.snapshotCaptureEnabled);
    }

    private void saveSettings() {
        ApiKeyStore.set(project, new String(apiKey.getPassword()));
        Object selected = model.getEditor().getItem();
        stateService.snapshot().model =
                selected == null ? MODELS[0] : selected.toString().trim();
        stateService.snapshot().snapshotCaptureEnabled = capture.isSelected();
    }

    private void refresh() {
        QuestState state = stateService.snapshot();
        int level = stateService.level();
        int current = QuestStateService.pointsInCurrentLevel(state.totalPoints);
        levelLabel.setText("Level " + level);
        pointsLabel.setText(state.totalPoints + " XP");
        progress.setValue(current);
        progressText.setText(current + " / 100 XP to Level " + (level + 1));

        QuestTask selected = taskList.getSelectedValue();
        taskModel.clear();
        for (QuestTask task : state.activeTasks) {
            taskModel.addElement(task);
        }
        if (!taskModel.isEmpty()) {
            int index = selected == null ? 0 : indexOf(selected.id);
            taskList.setSelectedIndex(index < 0 ? 0 : index);
        } else {
            details.setText("No active quests yet.");
        }
    }

    private int indexOf(String id) {
        for (int index = 0; index < taskModel.size(); index++) {
            if (Objects.equals(taskModel.get(index).id, id)) {
                return index;
            }
        }
        return -1;
    }

    private void showTask(QuestTask task) {
        if (task == null) {
            details.setText("Select a quest.");
            return;
        }
        String criteria = task.acceptanceCriteria.stream()
                .map(value -> "• " + value)
                .collect(Collectors.joining("\n"));
        details.setText(
                task.title + "\n\n"
                        + task.type + " · " + task.difficulty + " · " + task.points + " XP\n"
                        + "File: " + task.targetFile + "\n"
                        + "Test: " + task.testClass + "\n\n"
                        + task.objective + "\n\n"
                        + "Acceptance criteria\n" + criteria
                        + (task.pointsAwarded ? "\n\n✓ Completed" : "")
        );
        details.setCaretPosition(0);
    }

    private void generateTasks() {
        saveSettings();
        if (ApiKeyStore.get(project).isBlank()) {
            notifyUser("Enter a Gemini API key in Settings first", NotificationType.WARNING);
            return;
        }
        setBusy(true, "Analyzing tests and snapshots…");
        ApplicationManager.getApplication().invokeLater(() -> {
            FileDocumentManager.getInstance().saveAllDocuments();
            startTaskGeneration();
        });
    }

    private void startTaskGeneration() {
        ProgressManager.getInstance().run(new Task.Backgroundable(
                project,
                "Generating Test Quest tasks",
                true
        ) {
            private List<QuestTask> generated;
            private Exception failure;

            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    generated = new TaskGenerationService().generate(project);
                } catch (Exception exception) {
                    failure = exception;
                }
            }

            @Override
            public void onFinished() {
                if (failure != null) {
                    setBusy(false, "Generation failed: " + failure.getMessage());
                    notifyUser(failure.getMessage(), NotificationType.ERROR);
                } else {
                    setBusy(false, "Generated " + generated.size() + " new quests.");
                    refresh();
                }
            }
        });
    }

    private void verifyTask() {
        QuestTask selected = taskList.getSelectedValue();
        if (selected == null) {
            notifyUser("Select a quest to verify", NotificationType.WARNING);
            return;
        }
        setBusy(true, "Checking source rules and running " + selected.testClass + "…");
        ApplicationManager.getApplication().invokeLater(() -> {
            FileDocumentManager.getInstance().saveAllDocuments();
            startTaskVerification(selected);
        });
    }

    private void startTaskVerification(QuestTask selected) {
        ProgressManager.getInstance().run(new Task.Backgroundable(
                project,
                "Verifying Test Quest task",
                true
        ) {
            private VerificationResult result;
            private Exception failure;

            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    result = new TaskVerificationService().verify(project, selected);
                } catch (Exception exception) {
                    failure = exception;
                }
            }

            @Override
            public void onFinished() {
                if (failure != null) {
                    setBusy(false, "Verification failed: " + failure.getMessage());
                    notifyUser(failure.getMessage(), NotificationType.ERROR);
                    return;
                }
                String text = String.join("\n", result.checks);
                if (!result.testOutput.isBlank() && !result.successful) {
                    text += "\n\nTest output\n" + result.testOutput;
                }
                setBusy(false, text);
                refresh();
                if (result.successful) {
                    notifyUser(
                            "Quest complete: +" + selected.points + " XP",
                            NotificationType.INFORMATION
                    );
                }
            }
        });
    }

    private void setBusy(boolean busy, String message) {
        generate.setEnabled(!busy);
        verify.setEnabled(!busy);
        activity.setText(message == null ? "" : message);
        activity.setCaretPosition(0);
    }

    private void notifyUser(String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Test Quest")
                .createNotification(
                        message == null || message.isBlank() ? "Unknown error" : message,
                        type
                )
                .notify(project);
    }

    private static JLabel bold(String text) {
        JLabel label = new JBLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }
}
