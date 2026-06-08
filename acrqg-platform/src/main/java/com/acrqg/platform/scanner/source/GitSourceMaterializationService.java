package com.acrqg.platform.scanner.source;

import com.acrqg.platform.infra.net.OutboundUrlGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Git-backed implementation that checks out the exact task commit into a controlled temporary workspace. */
@Service
@Profile("worker")
public class GitSourceMaterializationService implements SourceMaterializationService {

    private static final Logger log = LoggerFactory.getLogger(GitSourceMaterializationService.class);
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$");
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_OUTPUT_CHARS = 16_384;

    private final Path workspaceRoot;

    public GitSourceMaterializationService(
            @Value("${app.scanner.workspace-root:${java.io.tmpdir}/acrqg-scanner-workspaces}") String workspaceRoot) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override
    public MaterializedSource materialize(SourceMaterializationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("source materialization request is required");
        }
        if (request.commitSha() == null || !COMMIT_SHA.matcher(request.commitSha().trim()).matches()) {
            throw new IllegalArgumentException("scanner source checkout requires an exact commit SHA");
        }
        String repoUrl = OutboundUrlGuard.requireHttpsPublicUrl(
                request.repoUrl(), "repository checkout URL").toString();
        try {
            Files.createDirectories(workspaceRoot);
            Path taskRoot = Files.createTempDirectory(workspaceRoot, "task-" + request.taskId() + "-")
                    .toAbsolutePath()
                    .normalize();
            ensureInsideWorkspace(taskRoot);
            Path workdir = taskRoot.resolve("repo");
            Path askpass = createAskpass(taskRoot, request.accessToken());
            try {
                GitEnv env = new GitEnv(askpass);
                runGit(List.of("git", "clone", "--no-checkout", repoUrl, workdir.toString()), taskRoot, env);
                runGit(List.of("git", "checkout", "--detach", request.commitSha().trim()), workdir, env);
                log.info("materialized source for taskId={} into {}", request.taskId(), workdir);
                return new MaterializedSource(workdir, taskRoot);
            } catch (RuntimeException ex) {
                deleteSilently(taskRoot);
                throw ex;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to create scanner workspace", ex);
        }
    }

    private Path createAskpass(Path taskRoot, String token) throws IOException {
        Path askpass = taskRoot.resolve("git-askpass.sh");
        String safeToken = token == null ? "" : token;
        String script = "#!/bin/sh\n"
                + "case \"$1\" in\n"
                + "  *Username*|*username*) printf '%s\\n' 'oauth2' ;;\n"
                + "  *) printf '%s\\n' " + shellSingleQuote(safeToken) + " ;;\n"
                + "esac\n";
        Files.writeString(askpass, script, StandardCharsets.UTF_8);
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(askpass, perms);
        } catch (UnsupportedOperationException ignored) {
            askpass.toFile().setExecutable(true, true);
        }
        return askpass;
    }

    private void runGit(List<String> command, Path directory, GitEnv gitEnv) {
        Path outputFile;
        try {
            outputFile = Files.createTempFile(directory, "git-", ".log");
        } catch (IOException ex) {
            throw new IllegalStateException("failed to create git output log", ex);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(directory.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(outputFile.toFile());
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("GIT_ASKPASS", gitEnv.askpass().toString());
        pb.environment().put("SSH_ASKPASS", gitEnv.askpass().toString());
        try {
            Process process = pb.start();
            boolean exited = process.waitFor(GIT_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            String output = readOutput(outputFile);
            if (!exited) {
                process.destroyForcibly();
                throw new IllegalStateException("git command timeout: " + command.get(1));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("git command failed: " + command.get(1) + ", output=" + output);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to start git command: " + command.get(1), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running git command: " + command.get(1), ex);
        }
    }

    private static String readOutput(Path outputFile) throws IOException {
        byte[] bytes = Files.readAllBytes(outputFile);
        return new String(bytes, 0, Math.min(bytes.length, MAX_OUTPUT_CHARS), StandardCharsets.UTF_8)
                .replaceAll("(?i)(token|password)=\\S+", "$1=***");
    }

    private void ensureInsideWorkspace(Path path) {
        if (!path.startsWith(workspaceRoot)) {
            throw new IllegalStateException("scanner workspace escaped root");
        }
    }

    private static String shellSingleQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void deleteSilently(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best effort
                        }
                    });
        } catch (IOException ignored) {
            // best effort
        }
    }

    private record GitEnv(Path askpass) {
    }
}
