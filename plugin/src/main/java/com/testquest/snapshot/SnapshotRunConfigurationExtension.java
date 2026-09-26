package com.testquest.snapshot;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.testquest.core.ProjectScanner;
import com.testquest.core.SnapshotRepository;
import com.testquest.state.QuestStateService;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SnapshotRunConfigurationExtension extends RunConfigurationExtension {
    @Override
    public <T extends RunConfigurationBase<?>> void updateJavaParameters(
            @NotNull T configuration,
            @NotNull JavaParameters parameters,
            @Nullable RunnerSettings runnerSettings
    ) throws ExecutionException {
        if (!(configuration instanceof JavaRunConfigurationBase)
                || !QuestStateService.getInstance(configuration.getProject())
                .snapshot().snapshotCaptureEnabled) {
            return;
        }

        try {
            Path agent = SnapshotAgentInstaller.install();
            Path root = ProjectScanner.projectRoot(configuration.getProject());
            Path snapshots = SnapshotRepository.directory(root);
            boolean alreadyInstalled = parameters.getVMParametersList().getList().stream()
                    .anyMatch(value -> value.startsWith("-javaagent:")
                            && value.contains("testquest-snapshot-agent"));
            if (!alreadyInstalled) {
                parameters.getVMParametersList().add("-javaagent:" + agent);
            }
            parameters.getVMParametersList().addProperty(
                    "testquest.snapshotDir",
                    snapshots.toString()
            );
        } catch (Exception exception) {
            throw new ExecutionException(
                    "Test Quest could not prepare Selenium snapshot capture",
                    exception
            );
        }
    }

    @Override
    public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
        return configuration instanceof JavaRunConfigurationBase;
    }
}
