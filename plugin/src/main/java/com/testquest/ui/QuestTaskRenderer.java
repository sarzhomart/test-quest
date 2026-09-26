package com.testquest.ui;

import com.intellij.ui.JBColor;
import com.testquest.model.QuestTask;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

public final class QuestTaskRenderer extends JPanel implements ListCellRenderer<QuestTask> {
    private final JLabel title = new JLabel();
    private final JLabel metadata = new JLabel();

    public QuestTaskRenderer() {
        super(new BorderLayout(0, 4));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        metadata.setForeground(JBColor.GRAY);
        add(title, BorderLayout.CENTER);
        add(metadata, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends QuestTask> list,
            QuestTask value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
    ) {
        title.setText(value.title);
        metadata.setText(
                value.type + " · " + value.difficulty + " · " + value.points + " XP"
                        + (value.pointsAwarded ? " · DONE" : "")
        );
        setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        title.setForeground(getForeground());
        return this;
    }
}

