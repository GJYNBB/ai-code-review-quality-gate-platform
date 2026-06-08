package com.acrqg.platform.scanner.source;

/** Immutable request for preparing a repository checkout for static scanners. */
public record SourceMaterializationRequest(
        long taskId,
        String repoUrl,
        String commitSha,
        String accessToken
) {
}
